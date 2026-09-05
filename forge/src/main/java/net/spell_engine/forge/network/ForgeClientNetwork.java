package net.spell_engine.forge.network;

import net.spell_engine.client.ClientNetwork;
import net.spell_engine.network.Packets;

/// Clientbound dispatch for {@link ForgeNetwork}: thin forwarders into the loader-agnostic
/// {@link ClientNetwork} (which references client-only classes). Kept in its own class so the
/// channel registration in {@link ForgeNetwork} never resolves client code on a dedicated server —
/// the registration lambdas only touch this class when a clientbound message is actually handled.
public class ForgeClientNetwork {
    public static void handleConfigSync(Packets.ConfigSync packet) {
        ClientNetwork.handleConfigSync(packet);
    }

    public static void handleSpellRegistrySync(Packets.SpellRegistrySync packet) {
        ClientNetwork.handleSpellRegistrySync(packet);
    }

    public static void handleSpellCooldown(Packets.SpellCooldown packet) {
        ClientNetwork.handleSpellCooldown(packet);
    }

    public static void handleSpellCooldownSync(Packets.SpellCooldownSync packet) {
        ClientNetwork.handleSpellCooldownSync(packet);
    }

    public static void handleSpellMessage(Packets.SpellMessage packet) {
        ClientNetwork.handleSpellMessage(packet);
    }

    public static void handleParticleEffects(Packets.ParticleEffects packet) {
        ClientNetwork.handleParticleEffects(packet);
    }

    public static void handleSpellAnimation(Packets.SpellAnimation packet) {
        ClientNetwork.handleSpellAnimation(packet);
    }

    public static void handleSpellContainerSync(Packets.SpellContainerSync packet) {
        ClientNetwork.handleSpellContainerSync(packet);
    }

    public static void handleAttackAvailable(Packets.AttackAvailable packet) {
        ClientNetwork.handleAttackAvailable(packet);
    }
}
