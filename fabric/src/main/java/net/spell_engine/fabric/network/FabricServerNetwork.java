package net.spell_engine.fabric.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.config.ServerConfig;
import net.spell_engine.internals.container.SpellAssignments;
import net.spell_engine.network.Packets;
import net.spell_engine.network.ServerNetwork;

import java.util.List;
import java.util.function.Consumer;

/// Fabric networking entrypoint: registers every payload type (both directions, so it runs on
/// client and server), the configuration tasks and the serverbound play receivers. Decoded packets
/// are forwarded to the loader-agnostic {@link ServerNetwork} handlers.
public class FabricServerNetwork {
    public static void init() {
        // Config stage
        PayloadTypeRegistry.configurationS2C().register(Packets.ConfigSync.PACKET_ID, Packets.ConfigSync.CODEC);
        PayloadTypeRegistry.configurationS2C().register(Packets.SpellRegistrySync.PACKET_ID, Packets.SpellRegistrySync.CODEC);
        PayloadTypeRegistry.configurationC2S().register(Packets.Ack.PACKET_ID, Packets.Ack.CODEC);

        ServerConfigurationConnectionEvents.CONFIGURE.register((handler, server) -> {
            // This if block is required! Otherwise the client gets stuck in connection screen
            // if the client cannot handle the packet.
            if (ServerConfigurationNetworking.canSend(handler, Packets.ConfigSync.ID)) {
                handler.addTask(new ConfigurationTask(SpellEngineMod.config));
            } else {
                handler.disconnect(Component.literal("Network configuration task not supported: " + ServerNetwork.CONFIG_TASK_NAME));
            }
        });
        ServerConfigurationConnectionEvents.CONFIGURE.register((handler, server) -> {
            if (ServerConfigurationNetworking.canSend(handler, Packets.SpellRegistrySync.ID)) {
                if (SpellAssignments.encoded.isEmpty()) {
                    throw new AssertionError("Spell registry is empty!");
                }
                handler.addTask(new SpellRegistrySyncTask(SpellAssignments.encoded));
            } else {
                handler.disconnect(Component.literal("Network configuration task not supported: " + ServerNetwork.SPELL_REGISTRY_TASK_NAME));
            }
        });
        ServerConfigurationNetworking.registerGlobalReceiver(Packets.Ack.PACKET_ID, (packet, context) -> {
            // Warning: if you do not call completeTask, the client gets stuck!
            if (packet.code().equals(ServerNetwork.CONFIG_TASK_NAME)) {
                context.networkHandler().completeTask(ConfigurationTask.KEY);
            }
            if (packet.code().equals(ServerNetwork.SPELL_REGISTRY_TASK_NAME)) {
                context.networkHandler().completeTask(SpellRegistrySyncTask.KEY);
            }
        });

        // Play stage
        PayloadTypeRegistry.playC2S().register(Packets.CastRequest.PACKET_ID, Packets.CastRequest.CODEC);
        PayloadTypeRegistry.playC2S().register(Packets.TargetStream.PACKET_ID, Packets.TargetStream.CODEC);
        PayloadTypeRegistry.playC2S().register(Packets.CastInput.PACKET_ID, Packets.CastInput.CODEC);
        PayloadTypeRegistry.playC2S().register(Packets.AttackPerform.PACKET_ID, Packets.AttackPerform.CODEC);
        PayloadTypeRegistry.playC2S().register(Packets.AttackFxBroadcast.PACKET_ID, Packets.AttackFxBroadcast.CODEC);
        PayloadTypeRegistry.playS2C().register(Packets.SpellCooldown.PACKET_ID, Packets.SpellCooldown.CODEC);
        PayloadTypeRegistry.playS2C().register(Packets.SpellCooldownSync.PACKET_ID, Packets.SpellCooldownSync.CODEC);
        PayloadTypeRegistry.playS2C().register(Packets.SpellMessage.PACKET_ID, Packets.SpellMessage.CODEC);
        PayloadTypeRegistry.playS2C().register(Packets.ParticleEffects.PACKET_ID, Packets.ParticleEffects.CODEC);
        PayloadTypeRegistry.playS2C().register(Packets.SpellAnimation.PACKET_ID, Packets.SpellAnimation.CODEC);
        PayloadTypeRegistry.playS2C().register(Packets.SpellContainerSync.PACKET_ID, Packets.SpellContainerSync.CODEC);
        PayloadTypeRegistry.playS2C().register(Packets.AttackAvailable.PACKET_ID, Packets.AttackAvailable.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(Packets.CastRequest.PACKET_ID, (packet, context) ->
                ServerNetwork.handleCastRequest(packet, context.server(), context.player()));
        ServerPlayNetworking.registerGlobalReceiver(Packets.TargetStream.PACKET_ID, (packet, context) ->
                ServerNetwork.handleTargetStream(packet, context.server(), context.player()));
        ServerPlayNetworking.registerGlobalReceiver(Packets.CastInput.PACKET_ID, (packet, context) ->
                ServerNetwork.handleCastInput(packet, context.server(), context.player()));
        ServerPlayNetworking.registerGlobalReceiver(Packets.AttackFxBroadcast.PACKET_ID, (packet, context) ->
                ServerNetwork.handleAttackFxBroadcast(packet, context.server(), context.player()));
        ServerPlayNetworking.registerGlobalReceiver(Packets.AttackPerform.PACKET_ID, (packet, context) ->
                ServerNetwork.handleAttackPerform(packet, context.server(), context.player()));
    }

    public record ConfigurationTask(ServerConfig config) implements net.minecraft.server.network.ConfigurationTask {
        public static final Type KEY = new Type(ServerNetwork.CONFIG_TASK_NAME);

        @Override
        public Type type() {
            return KEY;
        }

        @Override
        public void start(Consumer<Packet<?>> sender) {
            var packet = new Packets.ConfigSync(this.config);
            sender.accept(ServerConfigurationNetworking.createS2CPacket(packet));
        }
    }

    public record SpellRegistrySyncTask(List<String> encodedChunks) implements net.minecraft.server.network.ConfigurationTask {
        public static final Type KEY = new Type(ServerNetwork.SPELL_REGISTRY_TASK_NAME);

        @Override
        public Type type() {
            return KEY;
        }

        @Override
        public void start(Consumer<Packet<?>> sender) {
            var packet = new Packets.SpellRegistrySync(encodedChunks);
            sender.accept(ServerConfigurationNetworking.createS2CPacket(packet));
        }
    }
}
