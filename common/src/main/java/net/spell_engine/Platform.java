package net.spell_engine;

import com.mojang.serialization.Codec;
import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.network.packet.Packet;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.spell_engine.network.Packets;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Platform {
    public static final boolean Fabric;
    public static final boolean Forge;
    public static final boolean NeoForge;

    static
    {
        Fabric = getPlatformType() == Type.FABRIC;
        Forge  = getPlatformType() == Type.FORGE;
        NeoForge = getPlatformType() == Type.NEOFORGE;
    }

    public enum Type { FABRIC, FORGE, NEOFORGE }

    @ExpectPlatform
    protected static Type getPlatformType() {
        throw new AssertionError();
    }

    public interface Util {
        boolean isModLoaded(String modid);
        /// Whether the game is running in a development environment (dev workspace / loom run),
        /// as opposed to a packaged production install. Fabric: `FabricLoader.isDevelopmentEnvironment()`;
        /// NeoForge: `!FMLLoader.isProduction()`. Kept here so `common` needs no loader API for the check.
        boolean isDevelopmentEnvironment();
        void awakeSlotModCompat();
        void sendVanillaPacket_S2C(ServerPlayerEntity player, Packet<?> packet);
        /// Registers a summoned entity's default attribute container with the loader. Fabric registers
        /// imperatively; NeoForge buffers it for its `EntityAttributeCreationEvent`. Kept here so `common`
        /// stays free of loader-specific attribute-registration APIs.
        void registerSummonedEntityAttributes(EntityType<? extends LivingEntity> type, DefaultAttributeContainer.Builder builder);

        // MARK: Network hooks
        // Loader-native custom-payload send/query (Fabric: ServerPlayNetworking / ClientPlayNetworking,
        // Forge: SimpleChannel). `common` keeps every packet handler body; only the transport is here.
        // Payloads are SE's own `Packets.Payload` records (1.20.1 has no vanilla CustomPayload).

        /// Whether the given payload can be delivered to this player's connection.
        boolean networkS2C_CanSend(ServerPlayerEntity player, Identifier packetId);
        /// Send a clientbound custom payload to a single player.
        void networkS2C_Send(ServerPlayerEntity player, Packets.Payload payload);
        /// Send a serverbound custom payload from the client. Invoked on the physical client only.
        void networkC2S_Send(Packets.Payload payload);

        /// Register a synced datapack registry. Fabric: `DynamicRegistries.registerSynced` (imperative,
        /// during mod init); Forge: buffered for its mod-bus `DataPackRegistryEvent.NewRegistry`.
        /// A non-null `networkCodec` makes the registry client-synced (via the vanilla GameJoin registry
        /// payload on 1.20.1 — no separate packet, no configuration phase).
        <T> void registerSyncedDataRegistry(RegistryKey<Registry<T>> key, Codec<T> localCodec, Codec<T> networkCodec);
    }

    @ExpectPlatform
    public static Util util() {
        throw new AssertionError();
    }

    /// The server players currently tracking `entity` (receiving its position/entity updates),
    /// mirroring Fabric API's `PlayerLookup.tracking(Entity)`. Loader-neutral: asks the vanilla
    /// chunk storage for the players watching the entity's chunk (public API on 1.20.1, so no
    /// access widener / mixin is needed; on 1.21 this read the private entity-tracker listener set).
    /// Watching the chunk is a superset of tracking the entity (entity tracking distance never
    /// exceeds the chunk view distance), which is harmless for the FX/animation broadcasts this feeds.
    public static Collection<ServerPlayerEntity> tracking(Entity entity) {
        if (!(entity.getWorld() instanceof ServerWorld world)) {
            return List.of();
        }
        // 2-arg overload: plain "within watch distance" set (the 1-arg one is restricted to ticking chunks).
        var watching = world.getChunkManager().threadedAnvilChunkStorage.getPlayersWatchingChunk(entity.getChunkPos(), false);
        var players = new ArrayList<ServerPlayerEntity>(watching.size());
        for (var player : watching) {
            // Vanilla's entity tracker never lists the entity's own player as a tracker.
            if (player != entity) {
                players.add(player);
            }
        }
        return players;
    }
}
