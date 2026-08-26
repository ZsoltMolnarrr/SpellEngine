package net.spell_engine.neoforge.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.client.ClientNetwork;
import net.spell_engine.internals.container.SpellAssignments;
import net.spell_engine.network.Packets;
import net.spell_engine.network.ServerNetwork;

import java.util.function.Consumer;

/// NeoForge networking entrypoint (mod bus): native payload registration via `PayloadRegistrar` and
/// configuration tasks via `ICustomConfigurationTask`, with no Forgified Fabric API involved.
/// Decoded packets are forwarded to the loader-agnostic {@link ServerNetwork}/{@link ClientNetwork}.
@EventBusSubscriber(modid = SpellEngineMod.ID)
public class NetworkEvents {
    @SubscribeEvent
    public static void register(final RegisterConfigurationTasksEvent event) {
        event.register(new ConfigurationTask());
        event.register(new SpellRegistrySyncTask());
    }

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");

        // Configuration stage — server → client sync, client → server ack
        registrar.configurationToClient(Packets.ConfigSync.PACKET_ID, Packets.ConfigSync.CODEC, (packet, context) -> {
            ClientNetwork.handleConfigSync(packet);
            context.reply(new Packets.Ack(ServerNetwork.CONFIG_TASK_NAME));
        });
        registrar.configurationToClient(Packets.SpellRegistrySync.PACKET_ID, Packets.SpellRegistrySync.CODEC, (packet, context) -> {
            ClientNetwork.handleSpellRegistrySync(packet);
            context.reply(new Packets.Ack(ServerNetwork.SPELL_REGISTRY_TASK_NAME));
        });
        registrar.configurationToServer(Packets.Ack.PACKET_ID, Packets.Ack.CODEC, (packet, context) -> {
            if (packet.code().equals(ServerNetwork.CONFIG_TASK_NAME)) {
                context.finishCurrentTask(ConfigurationTask.KEY);
            }
            if (packet.code().equals(ServerNetwork.SPELL_REGISTRY_TASK_NAME)) {
                context.finishCurrentTask(SpellRegistrySyncTask.KEY);
            }
        });

        // Play stage — client → server
        registrar.playToServer(Packets.CastRequest.PACKET_ID, Packets.CastRequest.CODEC, (packet, context) -> {
            var player = (ServerPlayer) context.player();
            ServerNetwork.handleCastRequest(packet, player.level().getServer(), player);
        });
        registrar.playToServer(Packets.TargetStream.PACKET_ID, Packets.TargetStream.CODEC, (packet, context) -> {
            var player = (ServerPlayer) context.player();
            ServerNetwork.handleTargetStream(packet, player.level().getServer(), player);
        });
        registrar.playToServer(Packets.CastInput.PACKET_ID, Packets.CastInput.CODEC, (packet, context) -> {
            var player = (ServerPlayer) context.player();
            ServerNetwork.handleCastInput(packet, player.level().getServer(), player);
        });
        registrar.playToServer(Packets.AttackPerform.PACKET_ID, Packets.AttackPerform.CODEC, (packet, context) -> {
            var player = (ServerPlayer) context.player();
            ServerNetwork.handleAttackPerform(packet, player.level().getServer(), player);
        });
        registrar.playToServer(Packets.AttackFxBroadcast.PACKET_ID, Packets.AttackFxBroadcast.CODEC, (packet, context) -> {
            var player = (ServerPlayer) context.player();
            ServerNetwork.handleAttackFxBroadcast(packet, player.level().getServer(), player);
        });

        // Play stage — server → client
        registrar.playToClient(Packets.SpellCooldown.PACKET_ID, Packets.SpellCooldown.CODEC, (packet, context) ->
                ClientNetwork.handleSpellCooldown(packet));
        registrar.playToClient(Packets.SpellCooldownSync.PACKET_ID, Packets.SpellCooldownSync.CODEC, (packet, context) ->
                ClientNetwork.handleSpellCooldownSync(packet));
        registrar.playToClient(Packets.SpellMessage.PACKET_ID, Packets.SpellMessage.CODEC, (packet, context) ->
                ClientNetwork.handleSpellMessage(packet));
        registrar.playToClient(Packets.ParticleEffects.PACKET_ID, Packets.ParticleEffects.CODEC, (packet, context) ->
                ClientNetwork.handleParticleEffects(packet));
        registrar.playToClient(Packets.SpellAnimation.PACKET_ID, Packets.SpellAnimation.CODEC, (packet, context) ->
                ClientNetwork.handleSpellAnimation(packet));
        registrar.playToClient(Packets.SpellContainerSync.PACKET_ID, Packets.SpellContainerSync.CODEC, (packet, context) ->
                ClientNetwork.handleSpellContainerSync(packet));
        registrar.playToClient(Packets.AttackAvailable.PACKET_ID, Packets.AttackAvailable.CODEC, (packet, context) ->
                ClientNetwork.handleAttackAvailable(packet));
    }

    public record ConfigurationTask() implements ICustomConfigurationTask {
        public static final Type KEY = new Type(ServerNetwork.CONFIG_TASK_NAME);

        @Override
        public Type type() {
            return KEY;
        }

        @Override
        public void run(Consumer<CustomPacketPayload> sender) {
            sender.accept(new Packets.ConfigSync(SpellEngineMod.config));
        }
    }

    public record SpellRegistrySyncTask() implements ICustomConfigurationTask {
        public static final Type KEY = new Type(ServerNetwork.SPELL_REGISTRY_TASK_NAME);

        @Override
        public Type type() {
            return KEY;
        }

        @Override
        public void run(Consumer<CustomPacketPayload> sender) {
            sender.accept(new Packets.SpellRegistrySync(SpellAssignments.encoded));
        }
    }
}
