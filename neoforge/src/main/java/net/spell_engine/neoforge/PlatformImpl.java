package net.spell_engine.neoforge;

import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.ServerPlayerEntity;
import net.neoforged.fml.ModList;
import net.spell_engine.Platform;
import net.spell_engine.neoforge.compat.NeoForgeCompatFeatures;

public class PlatformImpl {
    public static Platform.Type getPlatformType() {
        return Platform.Type.NEOFORGE;
    }

    public static class NeoForgeUtil implements Platform.Util {
        @Override
        public boolean isModLoaded(String modid) {
            return ModList.get().isLoaded(modid);
        }

        @Override
        public void awakeSlotModCompat() {
            NeoForgeCompatFeatures.initSlotCompat();
        }

        @Override
        public void sendVanillaPacket_S2C(ServerPlayerEntity player, Packet<?> packet) {
            player.networkHandler.send(packet);
        }
    }
    private static final Platform.Util UTIL = new NeoForgeUtil();
    public static Platform.Util util() {
        return UTIL;
    }
}
