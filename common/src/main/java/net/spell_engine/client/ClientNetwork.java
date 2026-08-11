package net.spell_engine.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Language;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.client.animation.AnimatablePlayer;
import net.spell_engine.client.gui.HudMessages;
import net.spell_engine.internals.casting.SpellCasterClient;
import net.spell_engine.internals.casting.SpellCasterEntity;
import net.spell_engine.internals.container.SpellAssignments;
import net.spell_engine.internals.container.SpellContainerSource;
import net.spell_engine.network.Packets;
import net.spell_engine.fx.ParticleHelper;

/// Client-side packet handling. Loader-agnostic: holds only the handler bodies. Payload
/// registration and the configuration-phase Ack reply live in each loader's own client
/// network entrypoint (`FabricClientNetwork` / NeoForge `NetworkEvents`), which forwards
/// decoded packets here.
public class ClientNetwork {
    // MARK: Configuration stage

    public static void handleConfigSync(Packets.ConfigSync packet) {
        SpellEngineMod.config = packet.config();
    }

    public static void handleSpellRegistrySync(Packets.SpellRegistrySync packet) {
        SpellAssignments.decodeContent(packet.chunks());
    }

    // MARK: Play stage

    public static void handleParticleEffects(Packets.ParticleEffects packet) {
        var client = MinecraftClient.getInstance();
        var instructions = ParticleHelper.convertToInstructions(client.world, packet);
        client.execute(() -> {
            for (var instruction: instructions) {
                instruction.perform(client.world);
            }
        });
    }

    public static void handleSpellAnimation(Packets.SpellAnimation packet) {
        var client = MinecraftClient.getInstance();
        client.execute(() -> {
            var entity = client.world.getEntityById(packet.playerId());
            if (entity instanceof PlayerEntity player) {
                ((AnimatablePlayer) player).playSpellAnimation(packet.type(), packet.name(), packet.speed());
            }
        });
    }

    public static void handleSpellCooldown(Packets.SpellCooldown packet) {
        var client = MinecraftClient.getInstance();
        client.execute(() -> {
            if (client.world == null) return;
            var registry = SpellRegistry.from(client.world);
            var spell = registry.getEntry(packet.spellId());
            if (spell.isEmpty()) return;
            ((SpellCasterEntity) client.player).getCooldownManager().set(spell.get(), packet.duration());
        });
    }

    public static void handleSpellMessage(Packets.SpellMessage packet) {
        var client = MinecraftClient.getInstance();
        client.execute(() -> {
            var translation = Language.getInstance().get(packet.translationKey());
            HudMessages.INSTANCE.error(translation);
        });
    }

    public static void handleSpellCooldownSync(Packets.SpellCooldownSync packet) {
        var client = MinecraftClient.getInstance();
        client.execute(() -> {
            var cooldownManager = ((SpellCasterEntity) client.player).getCooldownManager();
            var cooldownsBefore = cooldownManager.spellsOnCooldown();
            cooldownManager.acceptSync(packet.baseTick(), packet.cooldowns());
            var cooldownsAfter = cooldownManager.spellsOnCooldown();
            HudMessages.INSTANCE.onCooldownsChanged(cooldownsBefore, cooldownsAfter);
        });
    }

    public static void handleSpellContainerSync(Packets.SpellContainerSync packet) {
        var client = MinecraftClient.getInstance();
        client.execute(() -> {
            var player = client.player;
            if (player != null) {
                var containers = ((SpellContainerSource.Owner) player).serverSideSpellContainers();
                containers.clear();
                containers.putAll(packet.containers());
            }
            SpellContainerSource.setDirty(client.player, SpellContainerSource.MAIN_HAND);
        });
    }

    public static void handleAttackAvailable(Packets.AttackAvailable packet) {
        var client = MinecraftClient.getInstance();
        client.execute(() -> {
            var player = client.player;
            if (player instanceof SpellCasterClient caster) {
                caster.onAttacksAvailable(packet.attacks());
            }
        });
    }
}
