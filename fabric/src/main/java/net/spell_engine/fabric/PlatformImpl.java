package net.spell_engine.fabric;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.Packet;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
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
        public void awakeSlotModCompat() {
            FabricCompatFeatures.initSlotCompat();
        }

        @Override
        public void sendVanillaPacket_S2C(ServerPlayerEntity player, Packet<?> packet) {
            player.networkHandler.sendPacket(packet);
        }

        @Override
        public void registerSummonedEntityAttributes(EntityType<? extends LivingEntity> type, DefaultAttributeContainer.Builder builder) {
            // Fabric registers default attributes imperatively — fine to call any time during init.
            FabricDefaultAttributeRegistry.register(type, builder.build());
        }

        @Override
        public boolean networkS2C_CanSend(ServerPlayerEntity player, Identifier packetId) {
            return ServerPlayNetworking.canSend(player, packetId);
        }

        @Override
        public void networkS2C_Send(ServerPlayerEntity player, CustomPayload payload) {
            ServerPlayNetworking.send(player, payload);
        }

        @Override
        public void networkC2S_Send(CustomPayload payload) {
            ClientPlayNetworking.send(payload);
        }

        @Override
        public <T> void registerSyncedDataRegistry(RegistryKey<Registry<T>> key, Codec<T> localCodec, Codec<T> networkCodec) {
            DynamicRegistries.registerSynced(key, localCodec, networkCodec);
        }
    }
    private static final Platform.Util UTIL = new FabricUtil();
    public static Platform.Util util() {
        return UTIL;
    }
}
