package net.spell_engine;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
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
        /// Registers a summoned entity's default attribute container with the loader. Fabric registers
        /// imperatively; NeoForge buffers it for its `EntityAttributeCreationEvent`. Kept here so `common`
        /// stays free of loader-specific attribute-registration APIs.
        void registerSummonedEntityAttributes(EntityType<? extends LivingEntity> type, DefaultAttributeContainer.Builder builder);
    }

    @ExpectPlatform
    public static Util util() {
        throw new AssertionError();
    }
}
