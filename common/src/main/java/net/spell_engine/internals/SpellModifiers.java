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
        return of(player, spellEntry).stream()
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
}
