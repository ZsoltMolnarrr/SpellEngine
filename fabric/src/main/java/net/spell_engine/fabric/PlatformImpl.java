package net.spell_engine.fabric;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.spell_engine.api.attachment.SyncedEntityData;
import org.jetbrains.annotations.Nullable;
import net.spell_engine.Platform;
import net.spell_engine.fabric.compat.FabricCompatFeatures;

public class PlatformImpl {
    public static Platform.Type getPlatformType() {
        return Platform.Type.FABRIC;
    }

    public static class FabricUtil implements Platform.Util {
        @Override
        public boolean isModLoaded(String modid) {
            return FabricLoader.getInstance().isModLoaded(modid);
        }

        @Override
        public boolean isDevelopmentEnvironment() {
            return FabricLoader.getInstance().isDevelopmentEnvironment();
        }

        @Override
        public void awakeSlotModCompat() {
            FabricCompatFeatures.initSlotCompat();
        }

        @Override
        public void sendVanillaPacket_S2C(ServerPlayer player, Packet<?> packet) {
            player.connection.send(packet);
        }

        @Override
        public void registerSummonedEntityAttributes(EntityType<? extends LivingEntity> type, AttributeSupplier.Builder builder) {
            // Fabric registers default attributes imperatively — fine to call any time during init.
            FabricDefaultAttributeRegistry.register(type, builder.build());
        }

        @Override
        public boolean networkS2C_CanSend(ServerPlayer player, Identifier packetId) {
            return ServerPlayNetworking.canSend(player, packetId);
        }

        @Override
        public void sendPacket(ServerPlayer player, net.minecraft.network.protocol.Packet<?> packet) {
            player.connection.send(packet);
        }

        @Override
        public void networkS2C_Send(ServerPlayer player, CustomPacketPayload payload) {
            ServerPlayNetworking.send(player, payload);
        }

        @Override
        public void networkC2S_Send(CustomPacketPayload payload) {
            ClientPlayNetworking.send(payload);
        }

        @Override
        public <T> void registerSyncedDataRegistry(ResourceKey<Registry<T>> key, Codec<T> localCodec, Codec<T> networkCodec) {
            DynamicRegistries.registerSynced(key, localCodec, networkCodec);
        }

        @Override
        public <T> SyncedEntityData<T> createSyncedEntityData(Identifier id, T defaultValue,
                                                              StreamCodec<? super RegistryFriendlyByteBuf, T> sync,
                                                              @Nullable Codec<T> persistence) {
            return new FabricSyncedEntityData<>(id, defaultValue, sync, persistence);
        }
    }
    private static final Platform.Util UTIL = new FabricUtil();
    public static Platform.Util util() {
        return UTIL;
    }
}
