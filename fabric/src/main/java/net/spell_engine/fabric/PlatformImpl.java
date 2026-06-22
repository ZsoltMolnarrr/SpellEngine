package net.spell_engine.fabric;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.ServerPlayerEntity;
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
    }
    private static final Platform.Util UTIL = new FabricUtil();
    public static Platform.Util util() {
        return UTIL;
    }
}
