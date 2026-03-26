package net.spell_engine.internals.target;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.Vec3d;
import net.spell_engine.api.entity.SpellEntityPredicates;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.internals.SpellHelper;
import net.spell_engine.utils.PatternMatching;
import net.spell_engine.utils.TargetHelper;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

public class SpellTarget {
    public enum Intent {
        HELPFUL, HARMFUL
    }
    public enum FocusMode {
        DIRECT, AREA
    }

    public record SearchResult(List<Entity> entities, @Nullable Vec3d location) {
        public static SearchResult empty() {
            return new SearchResult(List.of(), null);
        }
        public static SearchResult of(List<Entity> entities) {
            return new SearchResult(entities, null);
        }
        public static SearchResult of(Entity entity) {
            return new SearchResult(List.of(entity), null);
        }
        public static SearchResult of(Vec3d location) {
            return new SearchResult(List.of(), location);
        }
    }

    public static SearchResult findTargets(LivingEntity caster, RegistryEntry<Spell> spellEntry, SearchResult previous, boolean filterInvalidTargets) {
        var currentSpell = spellEntry.value();
        List<Entity> targets = List.of();
        var previousTargets = previous.entities;
        Vec3d location = null;
        if (currentSpell == null || currentSpell.impacts == null) {
            return new SearchResult(targets, location);
        }
        boolean fallbackToPreviousTargets = false;
        var focusMode = SpellHelper.focusMode(currentSpell);
        var targetType = currentSpell.target.type;
        var range = SpellHelper.getRange(caster, spellEntry) * caster.getScale();

        Predicate<Entity> selectionPredicate = (target) -> {
            var deliveryIntent = SpellHelper.deliveryIntent(currentSpell);
            boolean intentAllows = deliveryIntent.isPresent()
                    ? EntityRelations.actionAllowed(focusMode, deliveryIntent.get(), caster, target)
                    : false;
            for (var impact: currentSpell.impacts) {
                var intent = SpellHelper.impactIntent(impact.action);
                var newValue = impact.action.apply_to_caster
                        ? target == caster
                        : EntityRelations.actionAllowed(focusMode, intent, caster, target);
                intentAllows = intentAllows || newValue;
            }
            return !filterInvalidTargets || intentAllows;
        };

        switch (targetType) {
            case NONE -> {
            }
            case CASTER -> {
                targets = List.of(caster);
            }
            case AIM -> {
                fallbackToPreviousTargets = currentSpell.target.aim.sticky;
                var target = TargetHelper.targetFromRaycast(caster, range, selectionPredicate);
                if (target != null) {
                    targets = List.of(target);
                } else {
                    targets = List.of();
                }
            }
            case BEAM -> {
                targets = TargetHelper.targetsFromRaycast(caster, range, selectionPredicate);
            }
            case AREA -> {
                targets = TargetHelper.targetsFromArea(caster, range, currentSpell.target.area, selectionPredicate);
                var area = currentSpell.target.area;
                if (area != null && area.include_caster) {
                    targets.add(caster);
                }
            }
        }

        if (fallbackToPreviousTargets && targets.isEmpty()) {
            targets = previousTargets.stream()
                    .filter(entity -> {
                        return TargetHelper.isInLineOfSight(caster, entity) && !entity.isRemoved();
                    })
                    .toList();
        }

        var aim = currentSpell.target.aim;
        if (aim != null) {
            if (aim.use_caster_as_fallback && targets.isEmpty()) {
                targets = List.of(caster);
            }
            /// If no targets are found, use aim location for meteor style spells
            if (!aim.required && targets.isEmpty()) {
                location = TargetHelper.locationFromRayCast(caster, range);
            }
        }

        return new SearchResult(targets, location);
    }

    public static boolean evaluate(Entity testedEntity, @Nullable Entity otherEntity, @Nullable Spell.TargetCondition condition) {
        if (condition == null) {
            return true;
        }

        if (testedEntity instanceof LivingEntity livingEntity) {
            var healthPercent = livingEntity.getHealth() / livingEntity.getMaxHealth();
            // Watch out, inverse checks, to `return false`
            if (healthPercent < condition.health_percent_above || healthPercent > condition.health_percent_below) {
                return false;
            }
        }

        if (condition.entity_type != null) {
            if (!PatternMatching.matches(testedEntity.getType().getRegistryEntry(), RegistryKeys.ENTITY_TYPE, condition.entity_type)) {
                return false;
            }
        }

        if (condition.entity_predicate_id != null) {
            var predicate = SpellEntityPredicates.get(condition.entity_predicate_id);
            if (predicate == null) {
                return false;
            }
            var args = new SpellEntityPredicates.Input(testedEntity, otherEntity, condition.entity_predicate_param);
            if (!predicate.predicate().test(args)) {
                return false;
            }
        }
        return true;
    }
}
