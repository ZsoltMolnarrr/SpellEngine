package net.spell_engine.internals;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.internals.container.SpellContainerSource;
import net.spell_engine.utils.PatternMatching;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class SpellModifiers {
    public static List<Spell.Modifier> of(PlayerEntity player, RegistryEntry<Spell> spellEntry) {
        var spellId = spellEntry.getKey().get().getValue();
        var owner = (SpellContainerSource.Owner)player;
        var matchingModifiers = owner.spellModifierCache().get(spellId);
        if (matchingModifiers == null) {
            matchingModifiers = new ArrayList<Spell.Modifier>();
            var containers = owner.getSpellContainers();
            if (containers != null) {
                for (var entry: containers.modifiers()) {
                    var spell = entry.value();
                    for (var modifier: spell.modifiers) {
                        if (PatternMatching.matches(spellEntry, SpellRegistry.KEY, modifier.spell_pattern)) {
                            matchingModifiers.add(modifier);
                        }
                    }
                }
            }
            owner.spellModifierCache().put(spellId, matchingModifiers);
        }
        return matchingModifiers;
    }

    public static List<Spell.Modifier> ofImpact(PlayerEntity player, RegistryEntry<Spell> spellEntry,
                                          Spell.Impact impact) {
        return ofImpact((LivingEntity) player, spellEntry, impact, null);
    }

    /// Equipment/skill-tree modifiers are resolved for players only (cached per spell). The optional
    /// `chargeModifier` (a charge-ratio-scaled bonus carried on the ImpactContext) is prepended for
    /// any caster, so non-player casters still receive it.
    public static List<Spell.Modifier> of(LivingEntity caster, RegistryEntry<Spell> spellEntry,
                                          @Nullable Spell.Modifier chargeModifier) {
        var base = (caster instanceof PlayerEntity player) ? of(player, spellEntry) : List.<Spell.Modifier>of();
        if (chargeModifier == null) {
            return base;
        }
        var merged = new ArrayList<Spell.Modifier>(base.size() + 1);
        merged.add(chargeModifier); // prepend
        merged.addAll(base);
        return merged;
    }

    public static List<Spell.Modifier> ofImpact(LivingEntity caster, RegistryEntry<Spell> spellEntry,
                                          Spell.Impact impact, @Nullable Spell.Modifier chargeModifier) {
        return of(caster, spellEntry, chargeModifier).stream()
                .filter(modifier -> {
                    for(var filter: modifier.impact_filters) {
                        if (!impactMatches(impact, spellEntry, filter)) {
                            return false;
                        }
                    }
                    return true;
                })
                .toList();
    }

    private static boolean impactMatches(Spell.Impact impact, RegistryEntry<Spell> spellEntry, Spell.Modifier.ImpactFilter filter) {
        if (filter.school != null) {
            var school = impact.school != null ? impact.school : spellEntry.value().school;
            if (school != filter.school) {
                return false;
            }
        }
        if (filter.type != null && impact.action.type != filter.type) {
            return false;
        }
        return true;
    }

    public static float cooldownDeduction(PlayerEntity player, RegistryEntry<Spell> spellEntry) {
        var modifiers = of(player, spellEntry);
        float value = 0;
        for (var modifier: modifiers) {
            value += modifier.cooldown_duration_deduct;
        }
        return value;
    }


    public record ExtendedImpacts(List<Spell.Impact> impacts, @Nullable Spell.AreaImpact areaImpact) { }

    public static ExtendedImpacts extendedImpactsOf(LivingEntity caster, RegistryEntry<Spell> spellEntry) {
        var spell = spellEntry.value();
        var area_impact = spell.area_impact;
        var mutableImpacts = new ArrayList<>(spell.impacts);

        if (caster instanceof PlayerEntity player) {
            var modifiers = SpellModifiers.of(player, spellEntry);
            for (var modifier: modifiers) {
                if (modifier.mutate_impacts != null) {
                    switch (modifier.mutate_impacts) {
                        case PREPEND -> {
                            mutableImpacts.addAll(0, modifier.impacts);
                        }
                        case APPEND -> {
                            mutableImpacts.addAll(modifier.impacts);
                        }
                    }
                }
                if (modifier.replacing_area_impact != null) {
                    area_impact = modifier.replacing_area_impact;
                }
            }
        }
        return new ExtendedImpacts(mutableImpacts, area_impact);
    }

    /// Returns a deep copy of `modifier` with every magnitude field scaled by `r` (floats multiplied,
    /// ints rounded). Index/flag, list, enum and string fields are copied unchanged. Used to apply a
    /// charge-ratio-scaled bonus without mutating the shared template stored on the spell.
    public static Spell.Modifier scaledBy(Spell.Modifier modifier, float r) {
        var copy = new Spell.Modifier();
        // Copied unchanged (matchers / list / enum fields)
        copy.spell_pattern = modifier.spell_pattern;
        copy.mutate_impacts = modifier.mutate_impacts;
        copy.impacts = modifier.impacts;
        copy.replacing_area_impact = modifier.replacing_area_impact;
        copy.release = modifier.release;
        copy.impact_filters = modifier.impact_filters;
        // Deliberately dropped, not copied: an attack list has no magnitude to scale, so it would
        // appear in full at any ratio above `min_release_ratio` — "release at 21%, get the whole
        // extra swing". Charge therefore never adds melee attacks. Dropping it here also keeps the
        // attack ids resolvable from `AttackContext` on the way back from the client, which relies
        // on `allAttacksOf` producing the same list on both legs of the round trip.
        copy.melee_attacks = null;
        copy.additional_placements = modifier.additional_placements;
        copy.summon_attribute_scaling = modifier.summon_attribute_scaling;
        // Scaled top-level magnitudes
        copy.range_add = modifier.range_add * r;
        copy.channel_ticks_add = Math.round(modifier.channel_ticks_add * r);
        copy.knockback_multiply_base = modifier.knockback_multiply_base * r;
        copy.spawn_duration_add = modifier.spawn_duration_add * r;
        copy.teleport_distance_add = modifier.teleport_distance_add * r;
        copy.effect_amplifier_add = Math.round(modifier.effect_amplifier_add * r);
        copy.effect_amplifier_cap_add = Math.round(modifier.effect_amplifier_cap_add * r);
        copy.stash_amplifier_add = Math.round(modifier.stash_amplifier_add * r);
        copy.effect_duration_add = modifier.effect_duration_add * r;
        copy.cooldown_duration_deduct = modifier.cooldown_duration_deduct * r;
        copy.melee_momentum_add = modifier.melee_momentum_add * r;
        copy.melee_slipperiness_add = modifier.melee_slipperiness_add * r;
        copy.melee_damage_multiplier = modifier.melee_damage_multiplier * r;
        copy.projectile_scale_multiply = modifier.projectile_scale_multiply * r;
        copy.meteor_launch_radius_add = modifier.meteor_launch_radius_add * r;
        copy.summon_spawn_count_add = Math.round(modifier.summon_spawn_count_add * r);
        copy.summon_group_count_add = Math.round(modifier.summon_group_count_add * r);
        // Scaled nested objects (fresh copies, so the shared template is never mutated)
        if (modifier.power_modifier != null) {
            var pm = new Spell.Impact.Modifier();
            pm.power_multiplier = modifier.power_modifier.power_multiplier * r;
            pm.critical_chance_bonus = modifier.power_modifier.critical_chance_bonus * r;
            pm.critical_damage_bonus = modifier.power_modifier.critical_damage_bonus * r;
            copy.power_modifier = pm;
        }
        if (modifier.projectile_launch != null) {
            var pl = modifier.projectile_launch.copy(); // velocity is the only magnitude we scale
            pl.velocity *= r;                            // (extra_launch_* are index/flag fields)
            copy.projectile_launch = pl;
        }
        if (modifier.arrow_perks != null) {
            var ap = modifier.arrow_perks.copy();
            ap.pierce = Math.round(ap.pierce * r);
            // Multipliers are scaled around their neutral 1, so r=0 leaves the arrow untouched
            ap.damage_multiplier = 1 + (ap.damage_multiplier - 1) * r;
            ap.velocity_multiplier = 1 + (ap.velocity_multiplier - 1) * r;
            ap.knockback = 1 + (ap.knockback - 1) * r;
            copy.arrow_perks = ap;
        }
        if (modifier.projectile_perks != null) {
            var pk = modifier.projectile_perks.copy();
            pk.ricochet = Math.round(pk.ricochet * r);
            pk.ricochet_range *= r;
            pk.bounce = Math.round(pk.bounce * r);
            pk.pierce = Math.round(pk.pierce * r);
            pk.chain_reaction_size = Math.round(pk.chain_reaction_size * r);
            pk.chain_reaction_triggers = Math.round(pk.chain_reaction_triggers * r);
            pk.chain_reaction_increment = Math.round(pk.chain_reaction_increment * r);
            copy.projectile_perks = pk;
        }
        var sb = new Spell.Modifier.SummonBehaviourModifier();
        sb.actions_add = modifier.summon_behaviour.actions_add;
        sb.lifespan.spawn_ticks_add = Math.round(modifier.summon_behaviour.lifespan.spawn_ticks_add * r);
        sb.lifespan.active_seconds_add = Math.round(modifier.summon_behaviour.lifespan.active_seconds_add * r);
        sb.lifespan.despawn_ticks_add = Math.round(modifier.summon_behaviour.lifespan.despawn_ticks_add * r);
        copy.summon_behaviour = sb;
        return copy;
    }
}
