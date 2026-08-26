package net.spell_engine;

import com.mojang.serialization.Codec;
import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.core.Registry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.spell_engine.api.attachment.SyncedEntityData;
import org.jetbrains.annotations.Nullable;
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
        void sendVanillaPacket_S2C(ServerPlayer player, Packet<?> packet);
        /// Registers a summoned entity's default attribute container with the loader. Fabric registers
        /// imperatively; NeoForge buffers it for its `EntityAttributeCreationEvent`. Kept here so `common`
        /// stays free of loader-specific attribute-registration APIs.
        void registerSummonedEntityAttributes(EntityType<? extends LivingEntity> type, AttributeSupplier.Builder builder);

        // MARK: Network hooks
        // Loader-native custom-payload send/query, replacing Fabric API's ServerPlayNetworking /
        // ClientPlayNetworking. `common` keeps every packet handler body; only the transport is here.

        /// Whether the given payload can be delivered to this player's connection.
        boolean networkS2C_CanSend(ServerPlayer player, Identifier packetId);
        /// Send a clientbound custom payload to a single player.
        void networkS2C_Send(ServerPlayer player, CustomPacketPayload payload);
        /// Sends a vanilla packet to one player. Per loader because NeoForge's Yarn-patched jar names
        /// `ServerCommonNetworkHandler#sendPacket` `send` (LESSONS §4.15).
        void sendPacket(ServerPlayer player, net.minecraft.network.protocol.Packet<?> packet);
        /// Send a serverbound custom payload from the client. Invoked on the physical client only.
        void networkC2S_Send(CustomPacketPayload payload);

        /// Register a synced datapack registry, replacing Fabric API's `DynamicRegistries.registerSynced`.
        /// Fabric registers imperatively during init; NeoForge buffers it for its
        /// `DataPackRegistryEvent.NewRegistry`. A non-null `networkCodec` makes the registry client-synced.
        <T> void registerSyncedDataRegistry(ResourceKey<Registry<T>> key, Codec<T> localCodec, Codec<T> networkCodec);

        /// Creates a synced per-entity attachment, see `SyncedEntityData.create`. Fabric registers
        /// imperatively; NeoForge buffers the built type for its `ATTACHMENT_TYPES` register event.
        <T> SyncedEntityData<T> createSyncedEntityData(Identifier id, T defaultValue,
                                                       StreamCodec<? super RegistryFriendlyByteBuf, T> sync,
                                                       @Nullable Codec<T> persistence);
    }

    @ExpectPlatform
    public static Util util() {
        throw new AssertionError();
    }

    /// The server players currently tracking `entity` (receiving its position/entity updates),
    /// mirroring Fabric API's `PlayerLookup.tracking(Entity)`. Loader-neutral: reads the vanilla
    /// entity-tracker listener set directly (widened via the access widener), so no loader
    /// networking API is involved and behaviour is identical on Fabric and NeoForge.
    public static Collection<ServerPlayer> tracking(Entity entity) {
        if (!(entity.level() instanceof ServerLevel world)) {
            return List.of();
        }
        var tracker = world.getChunkSource().chunkMap.entityMap.get(entity.getId());
        if (tracker == null) {
            return List.of();
        }
        var players = new ArrayList<ServerPlayer>(tracker.seenBy.size());
        for (var listener : tracker.seenBy) {
            players.add(listener.getPlayer());
        }
        return players;
    }
}
