package net.spell_engine.neoforge;

import com.mojang.serialization.Codec;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.Packet;
import net.spell_engine.api.attachment.SyncedEntityData;
import org.jetbrains.annotations.Nullable;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.spell_engine.Platform;
import net.spell_engine.neoforge.compat.NeoForgeCompatFeatures;

public class PlatformImpl {
    public static Platform.Type getPlatformType() {
        return Platform.Type.NEOFORGE;
    }

    public static class NeoForgeUtil implements Platform.Util {
        @Override
        public boolean isModLoaded(String modid) {
            // LoadingModList (not ModList): populated during mod discovery, before any constructor runs,
            // so early compat gates in static initializers / init match Fabric's "resolved up front" timing.
            return LoadingModList.get().getModFileById(modid) != null;
        }

        @Override
        public boolean isDevelopmentEnvironment() {
            return !FMLLoader.getCurrent().isProduction();
        }

        @Override
        public void awakeSlotModCompat() {
            NeoForgeCompatFeatures.initSlotCompat();
        }

        @Override
        public void sendVanillaPacket_S2C(ServerPlayerEntity player, Packet<?> packet) {
            player.networkHandler.send(packet);
        }

        @Override
        public void registerSummonedEntityAttributes(EntityType<? extends LivingEntity> type, DefaultAttributeContainer.Builder builder) {
            // Buffered until EntityAttributeCreationEvent — NeoForge can't register attributes imperatively.
            SummonedEntityAttributeRegistrar.buffer(type, builder);
        }

        @Override
        public boolean networkS2C_CanSend(ServerPlayerEntity player, Identifier packetId) {
            // NeoForge negotiates channels during configuration; a connected client that reached the
            // play phase always supports our registered payloads, and PacketDistributor drops the rest.
            return true;
        }

        @Override
        public void networkS2C_Send(ServerPlayerEntity player, CustomPayload payload) {
            PacketDistributor.sendToPlayer(player, payload);
        }

        @Override
        public void networkC2S_Send(CustomPayload payload) {
            ClientPacketDistributor.sendToServer(payload);
        }

        @Override
        public <T> void registerSyncedDataRegistry(RegistryKey<Registry<T>> key, Codec<T> localCodec, Codec<T> networkCodec) {
            // Buffered until DataPackRegistryEvent.NewRegistry — NeoForge can't register these imperatively.
            SyncedDataRegistrar.buffer(key, localCodec, networkCodec);
        }

        @Override
        public <T> SyncedEntityData<T> createSyncedEntityData(Identifier id, T defaultValue,
                                                              PacketCodec<? super RegistryByteBuf, T> sync,
                                                              @Nullable Codec<T> persistence) {
            // Built now, registered in NeoForgeMod.register (RegisterEvent for ATTACHMENT_TYPES).
            return new NeoForgeSyncedEntityData<>(id, defaultValue, sync, persistence);
        }
    }
    private static final Platform.Util UTIL = new NeoForgeUtil();
    public static Platform.Util util() {
        return UTIL;
    }
}
