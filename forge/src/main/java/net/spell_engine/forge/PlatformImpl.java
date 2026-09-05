package net.spell_engine.forge;

import com.mojang.serialization.Codec;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.network.packet.Packet;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.LoadingModList;
import net.spell_engine.Platform;
import net.spell_engine.forge.compat.ForgeCompatFeatures;
import net.spell_engine.forge.network.ForgeNetwork;
import net.spell_engine.network.Packets;

public class PlatformImpl {
    public static Platform.Type getPlatformType() {
        return Platform.Type.FORGE;
    }

    public static class ForgeUtil implements Platform.Util {
        @Override
        public boolean isModLoaded(String modid) {
            // LoadingModList (not ModList): populated during mod discovery, before any constructor runs,
            // so early compat gates in static initializers / init match Fabric's "resolved up front" timing.
            return LoadingModList.get().getModFileById(modid) != null;
        }

        @Override
        public boolean isDevelopmentEnvironment() {
            return !FMLLoader.isProduction();
        }

        @Override
        public void awakeSlotModCompat() {
            ForgeCompatFeatures.initSlotCompat();
        }

        @Override
        public void sendVanillaPacket_S2C(ServerPlayerEntity player, Packet<?> packet) {
            player.networkHandler.sendPacket(packet);
        }

        @Override
        public void registerSummonedEntityAttributes(EntityType<? extends LivingEntity> type, DefaultAttributeContainer.Builder builder) {
            // Buffered until EntityAttributeCreationEvent — NeoForge can't register attributes imperatively.
            SummonedEntityAttributeRegistrar.buffer(type, builder);
        }

        @Override
        public boolean networkS2C_CanSend(ServerPlayerEntity player, Identifier packetId) {
            // All SE payloads ride one SimpleChannel negotiated at login; present ⇒ every id is supported.
            return ForgeNetwork.canSendToPlayer(player);
        }

        @Override
        public void networkS2C_Send(ServerPlayerEntity player, Packets.Payload payload) {
            ForgeNetwork.sendToPlayer(player, payload);
        }

        @Override
        public void networkC2S_Send(Packets.Payload payload) {
            ForgeNetwork.sendToServer(payload);
        }

        @Override
        public <T> void registerSyncedDataRegistry(RegistryKey<Registry<T>> key, Codec<T> localCodec, Codec<T> networkCodec) {
            // Buffered until DataPackRegistryEvent.NewRegistry — NeoForge can't register these imperatively.
            SyncedDataRegistrar.buffer(key, localCodec, networkCodec);
        }
    }
    private static final Platform.Util UTIL = new ForgeUtil();
    public static Platform.Util util() {
        return UTIL;
    }
}
