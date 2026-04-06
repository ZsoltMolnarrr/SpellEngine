package net.spell_engine.internals.delivery;

import com.google.common.base.Suppliers;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.internals.SpellHelper;
import net.spell_engine.internals.SpellTriggers;
import net.spell_engine.internals.arrow.ArrowHelper;
import net.spell_engine.internals.target.SpellTarget;
import net.spell_engine.utils.StatusEffectUtil;
import net.spell_power.api.SpellPower;

import java.util.HashMap;
import java.util.Map;

public class SpellStashHelper {
    public static void init() {
        ServerLifecycleEvents.SERVER_STARTING.register(SpellStashHelper::link);
    }

    private static void link(MinecraftServer minecraftServer) {
        var manager = minecraftServer.getRegistryManager();
        var registry = manager.get(SpellRegistry.KEY);
        registry.streamEntries().forEach(entry -> {
            var spell = entry.value();
            var id = entry.getKey().get().getValue();
            if (spell.deliver.type == Spell.Delivery.Type.STASH_EFFECT) {
                if (spell.deliver.stash_effect == null) {
                    System.err.println("Spell Engine: Stash spell linking error! Spell:" + id + " is missing `stash_effect`!");
                    return;
                }
                var stashEffect = spell.deliver.stash_effect;
                if (stashEffect.id == null || stashEffect.id.isEmpty()) {
                    System.err.println("Spell Engine: Stash spell linking error! Spell:" + id + " is missing `stash_effect.id`!");
                    return;
                }
                var trigger = stashEffect.triggers;
                if (trigger == null || trigger.isEmpty()) {
                    System.err.println("Spell Engine: Stash spell linking error! Spell:" + id + " is missing `stash_effect.trigger`!");
                    return;
                }
                var effectId = Identifier.of(stashEffect.id);
                var statusEffect = Registries.STATUS_EFFECT.get(effectId);
                if (statusEffect == null) {
                    System.err.println("Spell Engine: Stash spell linking error! Spell:" + id + " found no status effect for `stash_effect.id`: " + stashEffect.id);
                    return;
                }

                var stashes = SpellStash.getStashedSpells(statusEffect);
                for (var existingStash: stashes) {
                    if (existingStash.spell().equals(entry)) {
                        System.err.println("Spell Engine: Stash spell linking error! Spell:" + id + " already has a stash effect linked to " + stashEffect.id);
                        return;
                    }
                }

                SpellStash.configure(statusEffect, entry, stashEffect.triggers, stashEffect.impact_mode, stashEffect.consume, stashEffect.consumed_next_tick, stashEffect.consume_any_stacks);
            }
        });
    }

    public static void useStashes(SpellTriggers.Event event) {
        var caster = event.player;
        var world = caster.getWorld();
        Map<StatusEffectInstance, StatusEffectUtil.Diff> effectChanges = new HashMap<>();
        var activeEffects = Map.copyOf(caster.getActiveStatusEffects()); // Create copy to avoid concurrent modification
        for(var entry: activeEffects.entrySet()) {
            var effect = entry.getKey().value();
            var stack = entry.getValue();

            for (var stash: ((SpellStash) effect).getStashedSpells()) {
                var spellEntry = stash.spell();
                for (var trigger: stash.triggers()) {
                    if (spellEntry == null || trigger == null) { continue; }
                    if (!SpellTriggers.evaluateTrigger(spellEntry, trigger, event)) { continue; }

                    var consume = stash.consume();
                    var stacksAvailable = effectChanges.getOrDefault(stack, new StatusEffectUtil.Diff(stack, stack.getAmplifier(), stash.delayConsume() ? 1 : 0)).newAmplifier();
                    if (!stash.consume_any_stacks() && ((stacksAvailable + 1) < consume) ) {
                        continue;
                    }

                    switch (stash.impactMode()) {
                        case PERFORM -> {
                            var target = event.target(trigger);
                            var aoeSource = event.aoeSource(trigger);
                            var spell = stash.spell().value();
                            var power = SpellPower.getSpellPower(spell.school, event.player);
                            var impactContext = new SpellHelper.ImpactContext(1F, 1F, null, power, SpellTarget.FocusMode.DIRECT, 0);
                            if (target != null) {
                                impactContext = impactContext.position(target.getPos());
                            } else if (aoeSource != null) {
                                impactContext = impactContext.position(aoeSource.getPos());
                            } else {
                                impactContext = impactContext.position(caster.getPos());
                            }
                            SpellHelper.performImpacts(world, caster, target, aoeSource, spellEntry, spellEntry.value().impacts, impactContext);
                        }
                        case TRANSFER -> {
                            var arrow = event.arrow;
                            if (arrow != null) {
                                var shooter = event.player;
                                var trackers = Suppliers.memoize(() -> PlayerLookup.tracking(shooter));
                                ArrowHelper.onArrowShot(arrow, shooter, spellEntry, trackers);
                            }
                        }
                    }

                    if (consume != 0) {
                        effectChanges.put(stack, new StatusEffectUtil.Diff(stack, stacksAvailable - consume, stash.delayConsume() ? 1 : 0));
                    }
                    break; // Stop processing other triggers for this effect
                }
            }
        }

        var changes = effectChanges.values().stream().toList();
        StatusEffectUtil.applyChanges(caster, changes);
    }
}
