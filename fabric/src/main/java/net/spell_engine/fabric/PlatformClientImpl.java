package net.spell_engine.fabric;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.Packet;
import net.spell_engine.PlatformClient;

public class PlatformClientImpl {
    public static class FabricClientUtil implements PlatformClient.Util {
        @Override
        public void sendVanillaPacket_C2S(ClientPlayerEntity player, Packet<?> packet) {
            player.networkHandler.sendPacket(packet);
        }
    }

    private static final PlatformClient.Util UTIL = new FabricClientUtil();

    public static PlatformClient.Util util() {
        return UTIL;
    }
}