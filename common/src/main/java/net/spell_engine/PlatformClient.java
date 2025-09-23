package net.spell_engine;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.Packet;

public class PlatformClient {
    public interface Util {
        // These are likely necessary due to some mapping differences
        void sendVanillaPacket_C2S(ClientPlayerEntity player, Packet<?> packet);
    }

    @ExpectPlatform
    public static Util util() {
        throw new AssertionError();
    }
}
