package net.spell_engine.fabric.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.spell_engine.client.ClientNetwork;
import net.spell_engine.network.Packets;
import net.spell_engine.network.ServerNetwork;

/// Fabric client networking entrypoint: registers the clientbound receivers and replies to the
/// configuration tasks. Handlers live in the loader-agnostic {@link ClientNetwork}.
public class FabricClientNetwork {
    public static void init() {
        // Config stage
        ClientConfigurationNetworking.registerGlobalReceiver(Packets.ConfigSync.PACKET_ID, (packet, context) -> {
            ClientNetwork.handleConfigSync(packet);
            context.responseSender().sendPacket(new Packets.Ack(ServerNetwork.CONFIG_TASK_NAME));
        });
        ClientConfigurationNetworking.registerGlobalReceiver(Packets.SpellRegistrySync.PACKET_ID, (packet, context) -> {
            ClientNetwork.handleSpellRegistrySync(packet);
            context.responseSender().sendPacket(new Packets.Ack(ServerNetwork.SPELL_REGISTRY_TASK_NAME));
        });

        // Play stage
        ClientPlayNetworking.registerGlobalReceiver(Packets.ParticleEffects.PACKET_ID, (packet, context) ->
                ClientNetwork.handleParticleEffects(packet));
        ClientPlayNetworking.registerGlobalReceiver(Packets.SpellAnimation.PACKET_ID, (packet, context) ->
                ClientNetwork.handleSpellAnimation(packet));
        ClientPlayNetworking.registerGlobalReceiver(Packets.SpellCooldown.PACKET_ID, (packet, context) ->
                ClientNetwork.handleSpellCooldown(packet));
        ClientPlayNetworking.registerGlobalReceiver(Packets.SpellMessage.PACKET_ID, (packet, context) ->
                ClientNetwork.handleSpellMessage(packet));
        ClientPlayNetworking.registerGlobalReceiver(Packets.SpellCooldownSync.PACKET_ID, (packet, context) ->
                ClientNetwork.handleSpellCooldownSync(packet));
        ClientPlayNetworking.registerGlobalReceiver(Packets.SpellContainerSync.PACKET_ID, (packet, context) ->
                ClientNetwork.handleSpellContainerSync(packet));
        ClientPlayNetworking.registerGlobalReceiver(Packets.AttackAvailable.PACKET_ID, (packet, context) ->
                ClientNetwork.handleAttackAvailable(packet));
    }
}
