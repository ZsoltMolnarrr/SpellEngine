package net.spell_engine.forge;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.Packet;
import net.spell_engine.PlatformClient;

public class PlatformClientImpl {
    public static class ForgeClientUtil implements PlatformClient.Util {
        @Override
        public void sendVanillaPacket_C2S(ClientPlayerEntity player, Packet<?> packet) {
            player.networkHandler.sendPacket(packet);
        }
    }

    private static final PlatformClient.Util UTIL = new ForgeClientUtil();
    public static PlatformClient.Util util() {
        return UTIL;
    }
}