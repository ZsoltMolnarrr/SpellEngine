package net.spell_engine;

import com.mojang.serialization.Codec;
import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

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
        // Loader-native custom-payload send/query, replacing Fabric API's ServerPlayNetworking /
        // ClientPlayNetworking. `common` keeps every packet handler body; only the transport is here.

        /// Whether the given payload can be delivered to this player's connection.
        boolean networkS2C_CanSend(ServerPlayerEntity player, Identifier packetId);
        /// Send a clientbound custom payload to a single player.
        void networkS2C_Send(ServerPlayerEntity player, CustomPayload payload);
        /// Sends a vanilla packet to one player. Per loader because NeoForge's Yarn-patched jar names
        /// `ServerCommonNetworkHandler#sendPacket` `send` (LESSONS §4.15).
        void sendPacket(ServerPlayerEntity player, net.minecraft.network.packet.Packet<?> packet);
        /// Send a serverbound custom payload from the client. Invoked on the physical client only.
        void networkC2S_Send(CustomPayload payload);

        /// Register a synced datapack registry, replacing Fabric API's `DynamicRegistries.registerSynced`.
        /// Fabric registers imperatively during init; NeoForge buffers it for its
        /// `DataPackRegistryEvent.NewRegistry`. A non-null `networkCodec` makes the registry client-synced.
        <T> void registerSyncedDataRegistry(RegistryKey<Registry<T>> key, Codec<T> localCodec, Codec<T> networkCodec);
    }

    @ExpectPlatform
    public static Util util() {
        throw new AssertionError();
    }

    /// The server players currently tracking `entity` (receiving its position/entity updates),
    /// mirroring Fabric API's `PlayerLookup.tracking(Entity)`. Loader-neutral: reads the vanilla
    /// entity-tracker listener set directly (widened via the access widener), so no loader
    /// networking API is involved and behaviour is identical on Fabric and NeoForge.
    public static Collection<ServerPlayerEntity> tracking(Entity entity) {
        if (!(entity.getEntityWorld() instanceof ServerWorld world)) {
            return List.of();
        }
        var tracker = world.getChunkManager().chunkLoadingManager.entityTrackers.get(entity.getId());
        if (tracker == null) {
            return List.of();
        }
        var players = new ArrayList<ServerPlayerEntity>(tracker.listeners.size());
        for (var listener : tracker.listeners) {
            players.add(listener.getPlayer());
        }
        return players;
    }
}
