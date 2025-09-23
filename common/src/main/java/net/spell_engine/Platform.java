package net.spell_engine;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.ServerPlayerEntity;

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
        void awakeSlotModCompat();
        void sendVanillaPacket_S2C(ServerPlayerEntity player, Packet<?> packet);
    }

    @ExpectPlatform
    public static Util util() {
        throw new AssertionError();
    }
}
