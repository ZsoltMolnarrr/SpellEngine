package net.spell_engine.fabric;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.spell_engine.PlatformClient;

public class PlatformClientImpl {
    public static class FabricClientUtil implements PlatformClient.Util {
        @Override
        public void sendVanillaPacket_C2S(LocalPlayer player, Packet<?> packet) {
            player.connection.send(packet);
        }
    }

    private static final PlatformClient.Util UTIL = new FabricClientUtil();

    public static PlatformClient.Util util() {
        return UTIL;
    }
}