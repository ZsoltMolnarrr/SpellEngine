package net.spell_engine.fabric.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.spell_engine.client.ClientNetwork;
import net.spell_engine.network.Packets;

/// Fabric client networking entrypoint (Fabric API 0.92 / 1.20.1): registers the clientbound play
/// receivers. Handlers live in the loader-agnostic {@link ClientNetwork}. The join-sync packets
/// (config, spell assignments) arrive on the play channel right after JOIN — no configuration phase,
/// no acknowledgement (legacy 1.20.1 behaviour). Buffers are decoded on the netty thread; the handlers
/// hop to the client thread themselves where needed.
public class FabricClientNetwork {
    public static void init() {
        // Join sync
        ClientPlayNetworking.registerGlobalReceiver(Packets.ConfigSync.ID, (client, handler, buf, responseSender) ->
                ClientNetwork.handleConfigSync(Packets.ConfigSync.read(buf)));
        ClientPlayNetworking.registerGlobalReceiver(Packets.SpellRegistrySync.ID, (client, handler, buf, responseSender) ->
                ClientNetwork.handleSpellRegistrySync(Packets.SpellRegistrySync.read(buf)));

        // Play stage
        ClientPlayNetworking.registerGlobalReceiver(Packets.ParticleEffects.ID, (client, handler, buf, responseSender) ->
                ClientNetwork.handleParticleEffects(Packets.ParticleEffects.read(buf)));
        ClientPlayNetworking.registerGlobalReceiver(Packets.SpellAnimation.ID, (client, handler, buf, responseSender) ->
                ClientNetwork.handleSpellAnimation(Packets.SpellAnimation.read(buf)));
        ClientPlayNetworking.registerGlobalReceiver(Packets.SpellCooldown.ID, (client, handler, buf, responseSender) ->
                ClientNetwork.handleSpellCooldown(Packets.SpellCooldown.read(buf)));
        ClientPlayNetworking.registerGlobalReceiver(Packets.SpellMessage.ID, (client, handler, buf, responseSender) ->
                ClientNetwork.handleSpellMessage(Packets.SpellMessage.read(buf)));
        ClientPlayNetworking.registerGlobalReceiver(Packets.SpellCooldownSync.ID, (client, handler, buf, responseSender) ->
                ClientNetwork.handleSpellCooldownSync(Packets.SpellCooldownSync.read(buf)));
        ClientPlayNetworking.registerGlobalReceiver(Packets.SpellContainerSync.ID, (client, handler, buf, responseSender) ->
                ClientNetwork.handleSpellContainerSync(Packets.SpellContainerSync.read(buf)));
        ClientPlayNetworking.registerGlobalReceiver(Packets.AttackAvailable.ID, (client, handler, buf, responseSender) ->
                ClientNetwork.handleAttackAvailable(Packets.AttackAvailable.read(buf)));
    }
}
