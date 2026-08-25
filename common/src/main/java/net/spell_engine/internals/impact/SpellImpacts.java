package net.spell_engine.internals.impact;

import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import net.spell_engine.Platform;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.effect.StatusEffectClassification;
import net.spell_engine.api.entity.LivingEntityImmunity;
import net.spell_engine.api.entity.SpellEntity;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.event.SpellEvents;
import net.spell_engine.api.spell.event.SpellHandlers;
import net.spell_engine.api.spell.fx.Fx;
import net.spell_engine.api.spell.fx.Sound;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.api.spell.summon.AttributeScaling;
import net.spell_engine.api.spell.summon.SpellSummoned;
import net.spell_engine.api.spell.summon.SummonBehaviour;
import net.spell_engine.api.spell.weakness.SpellSchoolWeakness;
import net.spell_engine.api.tags.SpellEngineEntityTags;
import net.spell_engine.compat.CriticalStrikeCompat;
import net.spell_engine.entity.ConfigurableKnockback;
import net.spell_engine.entity.DamageSourceExtension;
import net.spell_engine.fx.ModelEffectHelper;
import net.spell_engine.fx.ParticleHelper;
import net.spell_engine.internals.delivery.EntityPlacements;
import net.spell_engine.internals.SpellExecution;
import net.spell_engine.internals.SpellExecution.ConditionResult;
import net.spell_engine.internals.SpellExecution.ImpactContext;
import net.spell_engine.internals.SpellModifiers;
import net.spell_engine.internals.SpellTriggers;
import net.spell_engine.internals.casting.SpellCaster;
import net.spell_engine.internals.container.SpellContainerSource;
import net.spell_engine.internals.cost.SpellCooldownManager;
import net.spell_engine.internals.target.EntityRelations;
import net.spell_engine.internals.target.SpellIntents;
import net.spell_engine.internals.target.SpellTarget;
import net.spell_engine.utils.PatternMatching;
import net.spell_engine.utils.SoundHelper;
import net.spell_engine.utils.StatusEffectUtil;
import net.spell_engine.utils.TargetHelper;
import net.spell_engine.utils.WorldScheduler;
import net.spell_power.api.SpellDamageSource;
import net.spell_power.api.SpellPower;
import net.spell_power.api.SpellSchool;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/// The IMPACT stage: what actually happens where a spell lands. Deliveries call in here — directly
/// for DIRECT hits, or later from a projectile, arrow, cloud or melee swing — and each impact's
/// action is executed against a target, together with the area impact it may fan out into.
public class SpellImpacts {

    private static final float knockbackDefaultStrength = 0.4F;

    // MARK: Entry points from deliveries

    public static void fallImpact(LivingEntity caster, Entity projectile, RegistryEntry<Spell> spellEntry, ImpactContext context) {
        var adjustedCenter = context.position().add(0, 1, 0); // Adding a bit of height to avoid raycast hitting the ground
        performImpacts(projectile.getEntityWorld(), caster, null, projectile, spellEntry, spellEntry.value().impacts, context.position(adjustedCenter));
    }

    public static boolean projectileImpact(LivingEntity caster, Entity projectile, Entity target, RegistryEntry<Spell> spellEntry, ImpactContext context) {
        return performImpacts(projectile.getEntityWorld(), caster, target, projectile, spellEntry, spellEntry.value().impacts, context);
    }

    public static boolean arrowImpact(LivingEntity caster, Entity projectile, Entity target, RegistryEntry<Spell> spellEntry, ImpactContext context) {
        var spell = spellEntry.value();
        if (spell.impacts != null) {
            if (context.power() == null) {
                context = context.power(SpellPower.getSpellPower(spell.school, caster));
            }
            return performImpacts(projectile.getEntityWorld(), caster, target, projectile, spellEntry, spell.impacts, context);
        }
        return false;
    }

    public static boolean meleeImpact(LivingEntity caster, List<Entity> targets, RegistryEntry<Spell> spellEntry, @Nullable ImpactContext context) {
        var spell = spellEntry.value();
        var anySuccess = false;
        if (spell.impacts != null) {
            if (context.power() == null) {
                context = context.power(SpellPower.getSpellPower(spell.school, caster));
            }

            var world = caster.getEntityWorld();
            var casterPos = caster.getEntityPos().add(0, caster.getHeight() / 2F, 0);

            for(var target: targets) {
                var position = target == caster
                        ? casterPos
                        : target.getEntityPos().add(0, target.getHeight() / 2F, 0).lerp(casterPos, 0.01F);
                var targetSpecificContext = context.position(position);
                var result = performImpacts(world, caster, target, target, spellEntry, spell.impacts, targetSpecificContext);
                anySuccess = anySuccess || result;
            }
        }
        return anySuccess;
    }

    // MARK: Area impact

    public static boolean lookupAndPerformAreaImpact(Spell.AreaImpact area_impact, RegistryEntry<Spell> spellEntry, LivingEntity caster, Entity exclude, @Nullable Entity aoeSource,
                                                  List<Spell.Impact> impacts, ImpactContext context, boolean additionalTargetLookup) {
        return lookupAndPerformAreaImpact(area_impact, spellEntry, caster, exclude, aoeSource, impacts, context, additionalTargetLookup, null);
    }

    /// `radiusOverride` replaces the configured (power-scaled) radius when non-null — used by growing
    /// clouds, whose effective radius changes over their lifetime. The parsed spell stays immutable.
    public static boolean lookupAndPerformAreaImpact(Spell.AreaImpact area_impact, RegistryEntry<Spell> spellEntry, LivingEntity caster, Entity exclude, @Nullable Entity aoeSource,
                                                  List<Spell.Impact> impacts, ImpactContext context, boolean additionalTargetLookup, @Nullable Float radiusOverride) {
        var center = context.position();
        var radius = radiusOverride != null ? radiusOverride : area_impact.combinedRadius(context.power().baseValue());

        var contextEntity = aoeSource != null ? aoeSource : caster;
        var targets = TargetHelper.targetsFromArea(contextEntity.getEntityWorld(), aoeSource, center, contextEntity.getRotationVector(), radius, area_impact.area, null);
        if (exclude != null) {
            targets.remove(exclude);
        }
        var result = applyAreaImpact(contextEntity.getEntityWorld(), caster, targets, radius, area_impact.area, spellEntry, impacts,
                context.target(SpellTarget.FocusMode.AREA), additionalTargetLookup, area_impact.execute_action_type);
        var areaVisuals = area_impact.visuals.resolved(Fx.Context.NONE);
        if (aoeSource != null) {
            // Anchored by coordinates rather than entity id: the source entity may die this very
            // tick (e.g. a falling projectile finishing), and entity-anchored FX would re-resolve
            // against its client-side position — racing the removal packet and placing the FX
            // wherever the client's own simulation of the entity happens to be.
            ParticleHelper.sendBatchesDetached(aoeSource, areaVisuals.particles);
        } else {
            ParticleHelper.sendBatches(center, caster, areaVisuals.particles);
        }

        SoundHelper.playSound(contextEntity.getEntityWorld(), contextEntity, area_impact.sound);
        ModelEffectHelper.spawn(contextEntity.getEntityWorld(), center, caster.getYaw(), areaVisuals.models,
                contextEntity instanceof LivingEntity le ? le : null);
        return result;
    }

    private static boolean applyAreaImpact(World world, LivingEntity caster, List<Entity> targets,
                                        float range, Spell.Target.Area area,
                                        RegistryEntry<Spell> spellEntry, List<Spell.Impact> impacts, ImpactContext context,
                                        boolean additionalTargetLookup, @Nullable Spell.Impact.Action.Type filteredAction) {
        double squaredRange = range * range;
        var center = context.position();
        var anyPerformed = false;
        for(var target: targets) {
            float distanceBasedMultiplier = 1F;
            switch (area.distance_dropoff) {
                case NONE -> { }
                case SQUARED -> {
                    distanceBasedMultiplier = (float) ((squaredRange - target.squaredDistanceTo(center)) / squaredRange);
                    distanceBasedMultiplier = Math.max(distanceBasedMultiplier, 0F);
                }
            }
            anyPerformed = performImpacts(world, caster, target, target, spellEntry, impacts, context
                            .distance(distanceBasedMultiplier),
                    additionalTargetLookup, filteredAction
            );
        }
        return anyPerformed;
    }

    private static boolean shouldApplyAreaImpact(Spell.AreaImpact areaImpact, EnumSet<Spell.Impact.Action.Type> performedActionTypes) {
        if (areaImpact.triggering_action_type == null)  {
            return true; // No specific action type, always apply
        }
        return performedActionTypes.contains(areaImpact.triggering_action_type);
    }

    // MARK: Impact list

    public static boolean performImpacts(World world, LivingEntity caster, @Nullable Entity target, Entity aoeSource, RegistryEntry<Spell> spellEntry, List<Spell.Impact> impacts, ImpactContext context) {
        return performImpacts(world, caster, target, aoeSource, spellEntry, impacts, context, true, null);
    }

    public static boolean performImpacts(World world, LivingEntity caster, @Nullable Entity target, Entity aoeSource,
                                         RegistryEntry<Spell> spellEntry, List<Spell.Impact> impacts, ImpactContext context,
                                         boolean additionalTargetLookup, @Nullable Spell.Impact.Action.Type filteredAction) {
        var trackers = target != null ? Platform.tracking(target) : null;
        SpellTarget.Intent selectedIntent = null;

        var extendedImpacts = SpellModifiers.extendedImpactsOf(caster, spellEntry);
        var area_impact = extendedImpacts.areaImpact();
        var mutableImpacts = extendedImpacts.impacts();

        var perform = true;
        if (additionalTargetLookup && area_impact != null && area_impact.force_indirect) {
            perform = false;
        }

        EnumSet<Spell.Impact.Action.Type> performedActionTypes = EnumSet.noneOf(Spell.Impact.Action.Type.class);
        if (perform) {
            for (var impact : mutableImpacts) {
                var intent = SpellIntents.impactIntent(impact.action);
                if (!impact.action.apply_to_caster // Only filtering for cases when another entity is actually targeted
                        && (selectedIntent != null && selectedIntent != intent)) {
                    // Filter out mixed intents
                    // So dual intent spells either damage or heal, and not do both
                    continue;
                }
                if (filteredAction != null && impact.action.type != filteredAction) {
                    // Filter out actions that are not of the specified type
                    continue;
                }
                if (additionalTargetLookup && !impact.action.allow_on_center_target) {
                    // Skip center target if additional target lookup is enabled
                    continue;
                }

                if (target != null) {
                    var result = performImpact(world, caster, target, spellEntry, impact, context, trackers);
                    if (result) {
                        performedActionTypes.add(impact.action.type);
                        if (!impact.action.apply_to_caster) {
                            // Caster-only impacts hit the caster, not the shared AOE targets, so they
                            // must not define the intent that gates subsequent target impacts (mirrors
                            // the apply_to_caster exemption in the mixed-intent filter above). Otherwise
                            // e.g. a self-buff would cancel the damage dealt to everyone else.
                            selectedIntent = intent;
                        }
                    }
                }
            }
        }

        if (area_impact != null
                && additionalTargetLookup
                && (shouldApplyAreaImpact(area_impact, performedActionTypes) || target == null) ) {
            var exclude = area_impact.force_indirect ? null : target;
            lookupAndPerformAreaImpact(area_impact, spellEntry, caster, exclude, aoeSource, impacts, context, false);
            if (caster instanceof PlayerEntity player) {
                ((WorldScheduler)world).schedule(0, () -> {
                    var location = target != null ? target.getEntityPos() : context.position();
                    SpellTriggers.onSpellAreaImpact(player, target, location, spellEntry);
                });
            }
        }

        var anyPerformed = !performedActionTypes.isEmpty();
        if (anyPerformed && caster instanceof PlayerEntity player) {
            ((WorldScheduler)world).schedule(0, () -> {
                SpellTriggers.onSpellImpactAny(player, target, aoeSource, spellEntry);
            });
        }

        return anyPerformed;
    }

    // MARK: Single impact

    private static boolean performImpact(World world, LivingEntity givenCaster, Entity target, RegistryEntry<Spell> spellEntry,
                                         Spell.Impact impact, ImpactContext context, Collection<ServerPlayerEntity> trackers) {
        if (!target.isAttackable()) {
            return false;
        }
        var success = false;
        var critical = false;
        boolean isKnockbackPushed = false;
        var spell = spellEntry.value();
        try {
            // Guards
            if (impact.chance < 1F && world.random.nextFloat() > impact.chance) {
                return false; // Skip impact if chance is not met
            }
            var school = impact.school != null ? impact.school : spell.school;
            var originalTarget = target;

            var effectiveCaster = context.effectiveCaster(world);
            var caster = effectiveCaster != null ? effectiveCaster : givenCaster;

            if (impact.action.apply_to_caster) {
                target = caster;
            } else {
                var intent = SpellIntents.impactIntent(impact.action);
                if (!EntityRelations.actionAllowed(context.focusMode(), intent, caster, target)) {
                    return false;
                }
            }
            // Merge school-level weaknesses with spell-level target modifiers
            var mergedTargetModifiers = new ArrayList<>(impact.target_modifiers);
            var schoolWeaknesses = SpellSchoolWeakness.getWeaknesses(school);
            if (!schoolWeaknesses.isEmpty()) {
                for (var schoolWeakness: schoolWeaknesses) {
                    if (schoolWeakness.impact_type() == null || schoolWeakness.impact_type() == impact.action.type) {
                        mergedTargetModifiers.addFirst(schoolWeakness.weakness()); // Prepend school weaknesses
                    }
                }
            }

            var conditionResult = evaluateImpactConditions(target, caster, mergedTargetModifiers);
            if (!conditionResult.allowed()) {
                return false;
            }
            var targetWasAlive = true;
            if (target instanceof LivingEntity livingEntity) {
                targetWasAlive = livingEntity.isAlive();
            }

            // Power calculation

            List<Spell.Modifier> spellModifiers = SpellModifiers.ofImpact(caster, spellEntry, impact, context.chargeModifier());

            double particleMultiplier = 1 * context.total(spellEntry);
            var power = SpellExecution.Power.resolve(spell, impact, caster, context.power(), originalTarget);

            var powerModifiers = new ArrayList<>(conditionResult.modifiers());
            for (var spellModifier: spellModifiers) {
                if (spellModifier.power_modifier != null) {
                    powerModifiers.add(spellModifier.power_modifier);
                }
            }
            var bonusPower = 1 + (powerModifiers.stream().map(modifier -> modifier.power_multiplier).reduce(0F, Float::sum));
            var bonusCritChance = powerModifiers.stream().map(modifier -> modifier.critical_chance_bonus).reduce(0F, Float::sum);
            var bonusCritDamage = powerModifiers.stream().map(modifier -> modifier.critical_damage_bonus).reduce(0F, Float::sum);
            power = new SpellPower.Result(power.school(),
                    power.baseValue() * bonusPower,
                    power.criticalChance() + bonusCritChance,
                    power.criticalDamage() + bonusCritDamage);

            power = SpellExecution.Power.clamped(power, impact.action);

            // Action execution

            switch (impact.action.type) {
                case DAMAGE -> {
                    var damageData = impact.action.damage;
                    var extraKnockback = 1F;
                    for (var spellModifier: spellModifiers) {
                        extraKnockback += spellModifier.knockback_multiply_base;
                    }

                    var knockbackMultiplier = Math.max(0F, damageData.knockback * context.total(spellEntry) * extraKnockback);
                    var vulnerability = SpellPower.Vulnerability.none;
                    var timeUntilRegen = target.timeUntilRegen;
                    if (target instanceof LivingEntity livingEntity) {
                        ((ConfigurableKnockback) livingEntity).pushKnockbackMultiplier_SpellEngine(context.hasOffset() ? 0 : knockbackMultiplier);
                        isKnockbackPushed = true;
                        if (damageData.bypass_iframes && SpellEngineMod.config.bypass_iframes) {
                            target.timeUntilRegen = 0;
                        }
                        vulnerability = SpellPower.getVulnerability(livingEntity, school);
                    }
                    var result = power.random(vulnerability);
                    critical = result.isCritical();
                    var amount = result.amount();
                    amount *= damageData.spell_power_coefficient;
                    amount *= context.total(spellEntry);
                    particleMultiplier = power.criticalDamage() + vulnerability.criticalDamageBonus();

                    ///
                    if (caster instanceof PlayerEntity player) {
                        SpellTriggers.onSpellImpactSpecific(player, target, spellEntry, impact, critical, Spell.Trigger.Stage.PRE);
                    }
                    ///

                    caster.onAttacking(target);
                    var damageSource = SpellDamageSource.create(school, caster);
                    if (critical) {
                        CriticalStrikeCompat.setCriticalStrike(damageSource, (float) power.criticalDamage());
                    }
                    ((DamageSourceExtension)damageSource).setSpellIndirect(context.focusMode() != SpellTarget.FocusMode.DIRECT);
                    target.damage((ServerWorld) target.getEntityWorld(), damageSource, (float) amount);

                    if (target instanceof LivingEntity livingEntity) {
                        ((ConfigurableKnockback)livingEntity).popKnockbackMultiplier_SpellEngine();
                        isKnockbackPushed = false;
                        target.timeUntilRegen = timeUntilRegen;
                        if (context.hasOffset()) {
                            var direction = context.knockbackDirection(livingEntity.getEntityPos()).negate(); // Negate for smart Vanilla API :)
                            livingEntity.takeKnockback(knockbackDefaultStrength * knockbackMultiplier, direction.x, direction.z);
                        }
                    }
                    success = true;
                }
                case HEAL -> {
                    if (target instanceof LivingEntity livingTarget) {
                        var healData = impact.action.heal;
                        particleMultiplier = power.criticalDamage();
                        var result = power.random();
                        critical = result.isCritical();
                        var amount = result.amount();
                        amount *= healData.spell_power_coefficient;
                        amount *= context.total(spellEntry);
                        if (context.isChanneled()) {
                            amount *= SpellPower.getHaste(caster, school);
                        }
                        ///
                        if (caster instanceof PlayerEntity player) {
                            SpellTriggers.onSpellImpactSpecific(player, target, spellEntry, impact, critical, Spell.Trigger.Stage.PRE);
                        }
                        ///
                        livingTarget.heal((float) amount);
                        if (SpellEvents.HEAL.isListened()) {
                            float finalAmount = (float) amount;
                            SpellEvents.HEAL.invoke((listener) -> listener.onHeal(new SpellEvents.HealEvent.Args(caster, spellEntry, livingTarget, finalAmount)));
                        }
                        success = true;
                    }
                }
                case STATUS_EFFECT -> {
                    var data = impact.action.status_effect;
                    if (target instanceof LivingEntity livingTarget) {
                        Optional<RegistryEntry<StatusEffect>> optionalEffect = Optional.empty();
                        List<StatusEffectInstance> removalSelection = List.of();
                        if (data.remove != null) {
                            var effects = livingTarget.getStatusEffects()
                                    .stream().filter(instance ->
                                            instance.getEffectType().value().isBeneficial() == data.remove.select_beneficial
                                            && PatternMatching.matches(instance.getEffectType(), RegistryKeys.STATUS_EFFECT, data.remove.id)
                                            && (data.remove.movement_impairing == null
                                                || StatusEffectClassification.isMovementImpairing(instance.getEffectType()) == data.remove.movement_impairing)
                                    )
                                    .toList();
                            if (effects.isEmpty()) {
                                return false;
                            }
                            removalSelection = switch (data.remove.selector) {
                                case RANDOM -> List.of(effects.get(world.random.nextInt(effects.size())));
                                case FIRST -> List.of(effects.getFirst());
                                case ALL -> effects;
                            };
                            optionalEffect = Optional.of(removalSelection.getFirst()).map(StatusEffectInstance::getEffectType);
                        } else {
                            var id = Identifier.of(data.effect_id);
                            optionalEffect = Optional.of(Registries.STATUS_EFFECT.getEntry(id).get());
                        }
                        if (optionalEffect.isEmpty()) {
                            return false;
                        }
                        var effect = optionalEffect.get();

                        if(!underApplyLimit(power, livingTarget, school, data.apply_limit)) {
                            return false;
                        }
                        var extraDuration = 0F;
                        var extraAmplifier = 0;
                        var extraCap = 0;
                        for (var spellModifier: spellModifiers) {
                            extraDuration += spellModifier.effect_duration_add;
                            extraAmplifier += spellModifier.effect_amplifier_add;
                            extraCap += spellModifier.effect_amplifier_cap_add;
                        }
                        var amplifier = data.amplifier + (int)(data.amplifier_power_multiplier * power.nonCriticalValue());
                        amplifier += extraAmplifier;
                        switch (data.apply_mode) {
                            case ADD, SET -> {
                                if (target.getType().isIn(SpellEngineEntityTags.bosses)
                                        && (StatusEffectClassification.isMovementImpairing(effect) || StatusEffectClassification.disablesMobAI(effect) ) ) {
                                    return false;
                                }
                                var duration = Math.round((data.duration + extraDuration) * 20F);

                                var showParticles = data.show_particles;
                                var cap = data.amplifier_cap
                                        + (int)(data.amplifier_cap_power_multiplier * power.nonCriticalValue())
                                        + extraCap;

                                if (data.apply_mode == Spell.Impact.Action.StatusEffect.ApplyMode.ADD) {
                                    var currentEffect = livingTarget.getStatusEffect(effect);

                                    var increment = amplifier;

                                    int newAmplifier = Math.max(increment - 1, 0);
                                    if (currentEffect != null) {
                                        var currentAmplifier = currentEffect.getAmplifier();
                                        var incrementedAmplifier = currentAmplifier + increment;
                                        // cap <= 0 means "no cap", matching the SET branch below. Without this
                                        // guard a zero cap would clamp every stack back to 0.
                                        newAmplifier = cap > 0 ? Math.min(incrementedAmplifier, cap) : incrementedAmplifier;
                                        if (!data.refresh_duration) {
                                            if (currentAmplifier == newAmplifier) {
                                                return false;
                                            }
                                            duration = currentEffect.getDuration();
                                        }
                                    }
                                    amplifier = newAmplifier;
                                } else {
                                    if (cap > 0) {
                                        amplifier = Math.min(amplifier, cap);
                                    }
                                }
                                ///
                                if (caster instanceof PlayerEntity player) {
                                    SpellTriggers.onSpellImpactSpecific(player, target, spellEntry, impact, critical, Spell.Trigger.Stage.PRE);
                                }
                                ///
                                var instance = new StatusEffectInstance(effect, duration, amplifier, false, showParticles, true);
                                livingTarget.addStatusEffect(instance, caster);
                                success = true;
                            }
                            case REMOVE -> {
                                if (data.amplifier_cap > 0) {
                                    amplifier = Math.min(amplifier, data.amplifier_cap);
                                }
                                if (!removalSelection.isEmpty()) {
                                    ///
                                    if (caster instanceof PlayerEntity player) {
                                        SpellTriggers.onSpellImpactSpecific(player, target, spellEntry, impact, critical, Spell.Trigger.Stage.PRE);
                                    }
                                    ///
                                    var diffs = new ArrayList<StatusEffectUtil.Diff>();
                                    for (var instance: removalSelection) {
                                        var newAmplifier = (amplifier > 0) ? (instance.getAmplifier() - amplifier) : -1;
                                        diffs.add(new StatusEffectUtil.Diff(instance, newAmplifier));
                                    }
                                    StatusEffectUtil.applyChanges(livingTarget, diffs);

                                    success = true;
                                }
                            }
                        }
                    }
                }
                case FIRE -> {
                    ///
                    if (caster instanceof PlayerEntity player) {
                        SpellTriggers.onSpellImpactSpecific(player, target, spellEntry, impact, critical, Spell.Trigger.Stage.PRE);
                    }
                    ///
                    var data = impact.action.fire;
                    target.setOnFireFor(data.duration);
                    if (target.getFireTicks() > 0) {
                        target.setFireTicks(target.getFireTicks() + data.tick_offset);
                    }
                    success = target.isOnFire();
                }
                case SPAWN -> {
                    var spawns = impact.action.spawns;
                    if (spawns == null || spawns.isEmpty()) {
                        return false;
                    }

                    float extraTimeToLive = 0;
                    for (var spellModifier: spellModifiers) {
                        extraTimeToLive += spellModifier.spawn_duration_add;
                    }

                    for(var data: spawns) {
                        var mutableData = data.copy();
                        mutableData.time_to_live_seconds += extraTimeToLive;
                        var id = Identifier.of(mutableData.entity_type_id);
                        var type = Registries.ENTITY_TYPE.get(id);

                        var entity = (Entity)type.create(world, SpawnReason.MOB_SUMMONED);
                        EntityPlacements.applyEntityPlacement(entity, caster, target.getEntityPos(), mutableData.placement);
                        if (entity instanceof SpellEntity.Spawned spellSpawnedEntity) {
                            var args = new SpellEntity.Spawned.Args(caster, spellEntry, mutableData, context);
                            spellSpawnedEntity.onSpawnedBySpell(args);
                        }
                        ///
                        if (caster instanceof PlayerEntity player) {
                            SpellTriggers.onSpellImpactSpecific(player, target, spellEntry, impact, critical, Spell.Trigger.Stage.PRE);
                        }
                        ///
                        ((WorldScheduler)world).schedule(mutableData.delay_ticks, () -> {
                            world.spawnEntity(entity);
                        });
                        success = true;
                    }
                }
                case TELEPORT -> {
                    var data = impact.action.teleport;
                    if (target instanceof LivingEntity livingTarget) {
                        LivingEntity teleportedEntity = null;
                        Vec3d destination = null;
                        Vec3d startingPosition = null;
                        Float applyRotation = null;
                        switch (data.mode) {
                            case FORWARD -> {
                                teleportedEntity = livingTarget;
                                var forward = data.forward;
                                var look = target.getRotationVector();
                                startingPosition = target.getEntityPos();
                                var distance = forward.distance;
                                for (var spellModifier: spellModifiers) {
                                    distance += spellModifier.teleport_distance_add;
                                }
                                distance = Math.max(0, distance);
                                destination = TargetHelper.findTeleportDestination(teleportedEntity, look, distance, data.required_clearance_block_y);
                                var groundJustBelow = TargetHelper.findSolidBlockBelow(teleportedEntity, destination, target.getEntityWorld(), -1.5F);
                                if (groundJustBelow != null) {
                                    destination = groundJustBelow;
                                }
                            }
                            case BEHIND_TARGET -> {
                                if (livingTarget == caster) {
                                    return false;
                                }
                                var look = target.getRotationVector();
                                var distance = 1F;
                                if (data.behind_target != null) {
                                    distance = data.behind_target.distance;
                                }
                                teleportedEntity = caster;
                                startingPosition = caster.getEntityPos();
                                destination = target.getEntityPos().add(look.multiply(-distance));
                                var groundJustBelow = TargetHelper.findSolidBlockBelow(teleportedEntity, destination, target.getEntityWorld(), -1.5F);
                                if (groundJustBelow != null) {
                                    destination = groundJustBelow;
                                }

                                double x = look.x;
                                double z = look.z;
                                // Calculate yaw using arctangent function
                                float yaw = (float) Math.toDegrees(Math.atan2(-x, z));
                                // Normalize yaw to the range [0, 360)
                                yaw = yaw < 0 ? yaw + 360 : yaw;
                                applyRotation = yaw;
                            }
                        }
                        // Fizzle: when a `minimum_distance` is configured, abort the teleport if the resolved
                        // destination would move the caster less than that many blocks (straight-line) — or if no
                        // safe destination was found at all. Returning `false` here means the impact never
                        // succeeds, so the delivery completion skips cost + cooldown.
                        if (data.minimum_distance > 0 && teleportedEntity != null && startingPosition != null) {
                            boolean farEnough = false;
                            if (destination != null) {
                                farEnough = destination.squaredDistanceTo(startingPosition) >= (data.minimum_distance * data.minimum_distance);
                            }
                            if (!farEnough) {
                                if (data.fizzle != null) {
                                    var fizzleVisuals = data.fizzle.visuals.resolved(Fx.Context.NONE);
                                    ParticleHelper.sendBatches(teleportedEntity, fizzleVisuals.particles, false);
                                    ModelEffectHelper.spawn(world, teleportedEntity.getEntityPos(), teleportedEntity.getYaw(),
                                            fizzleVisuals.models, teleportedEntity);
                                    SoundHelper.playSound(world, teleportedEntity, data.fizzle.sound);
                                }
                                return false;
                            }
                        }
                        if (destination != null && startingPosition != null && teleportedEntity != null) {
                            if (data.depart != null) {
                                var departVisuals = data.depart.resolved(Fx.Context.NONE);
                                ParticleHelper.sendBatches(teleportedEntity, departVisuals.particles, false);
                                ModelEffectHelper.spawn(world, startingPosition, teleportedEntity.getYaw(), departVisuals.models, teleportedEntity);
                            }
                            world.emitGameEvent(GameEvent.TELEPORT, startingPosition, GameEvent.Emitter.of(teleportedEntity));

                            if (applyRotation != null
                                    && teleportedEntity instanceof ServerPlayerEntity serverPlayer
                                    && world instanceof ServerWorld serverWorld) {
                                ///
                                if (caster instanceof PlayerEntity player) {
                                    SpellTriggers.onSpellImpactSpecific(player, target, spellEntry, impact, critical, Spell.Trigger.Stage.PRE);
                                }
                                ///
                                serverPlayer.teleport(serverWorld, destination.x, destination.y, destination.z, java.util.Set.of(), applyRotation, serverPlayer.getPitch(), false);
                                // teleportedEntity.teleport(destination.x, destination.y, destination.z, new HashSet<>(), applyRotation, 0);
                            } else {
                                teleportedEntity.teleport((ServerWorld) teleportedEntity.getEntityWorld(), destination.x, destination.y, destination.z, java.util.Set.of(), teleportedEntity.getYaw(), teleportedEntity.getPitch(), false);
                            }
                            success = true;

                            if (data.arrive != null) {
                                var arriveVisuals = data.arrive.resolved(Fx.Context.NONE);
                                ParticleHelper.sendBatches(teleportedEntity, arriveVisuals.particles, false);
                                ModelEffectHelper.spawn(world, teleportedEntity.getEntityPos(), teleportedEntity.getYaw(), arriveVisuals.models, teleportedEntity);
                            }
                        }
                    }
                }
                case COOLDOWN -> {
                    var cooldown = impact.action.cooldown;
                    var modified = false;
                    if (cooldown != null && target instanceof PlayerEntity playerTarget) {
                        ///
                        if (caster instanceof PlayerEntity player) {
                            SpellTriggers.onSpellImpactSpecific(player, target, spellEntry, impact, critical, Spell.Trigger.Stage.PRE);
                        }
                        ///
                        var cooldownManager = ((SpellCaster.Player)playerTarget).getCooldownManager();
                        if (cooldown.actives != null) {
                            var spells = SpellContainerSource.activeSpellsOf(playerTarget);
                            modified = modified || modifyCooldowns(spells, cooldown.actives, cooldownManager);
                        }
                        if (cooldown.passives != null) {
                            var spells = SpellContainerSource.passiveSpellsOf(playerTarget);
                            modified = modified || modifyCooldowns(spells, cooldown.passives, cooldownManager);
                        }
                        if (modified) {
                            cooldownManager.update(false);
                            cooldownManager.pushSync();
                        }
                    }
                    success = modified;
                }
                case AGGRO -> {
                    if (target instanceof MobEntity mob) {
                        // Ignoring taunt data, as it is empty currently
                        var aggroData = impact.action.aggro;
                        if (aggroData == null) {
                            return false;
                        }
                        if (aggroData.only_if_targeted && mob.getTarget() != caster) {
                            return false; // Only taunt if the mob is already targeting the caster
                        }
                        // mob.setTarget(tauntData.reverse ? null : caster);
                        switch (aggroData.mode) {
                            case SET -> {
                                mob.setTarget(caster);
                            }
                            case CLEAR -> {
                                mob.setTarget(null);
                            }
                        }
                        success = true;
                    }
                }
                case DISRUPT -> {
                    if (target instanceof LivingEntity livingTarget) {
                        var disrupt = impact.action.disrupt;
                        if (target instanceof PlayerEntity playerTarget) {
                             if (disrupt.shield_blocking && playerTarget.isBlocking()) {
                                 // vanilla disableShield() is gone: cooldown the blocking item and stop using it
                                 playerTarget.getItemCooldownManager().set(playerTarget.getActiveItem(), 100);
                                 playerTarget.clearActiveItem();
                                 success = true;
                             } else if (disrupt.item_usage_seconds > 0 && playerTarget.isUsingItem()) {
                                 var activeStack = playerTarget.getActiveItem();
                                 playerTarget.getItemCooldownManager().set(activeStack, (int) (disrupt.item_usage_seconds * 20F));
                                 success = true;
                             }
                        } else {
                            if (disrupt.shield_blocking && livingTarget.isBlocking()) {
                                livingTarget.clearActiveItem();
                                success = true;
                            } else if (disrupt.item_usage_seconds > 0 && livingTarget.isUsingItem()) {
                                livingTarget.clearActiveItem();
                                success = true;
                            }
                        }
                    }
                }
                case IMMUNITY -> {
                    var data = impact.action.immunity;
                    if (target instanceof LivingEntity livingTarget
                            && impact.action.immunity != null) {
                        DamageType type = null;
                        TagKey<DamageType> typeTagKey = null;
                        if (data.damage_type != null) {
                            if (data.damage_type.startsWith(PatternMatching.TAG_PREFIX)) {
                                var id = Identifier.of(data.damage_type.substring(PatternMatching.TAG_PREFIX.length()));
                                typeTagKey = TagKey.of(RegistryKeys.DAMAGE_TYPE, id);
                            } else {
                                var id = Identifier.of(data.damage_type);
                                var registry = world.getRegistryManager().getOrThrow(RegistryKeys.DAMAGE_TYPE);
                                type = registry.getOptionalValue(id).orElse(null);
                            }
                        }
                        if (data.duration_ticks > 0) {
                            LivingEntityImmunity.apply(livingTarget, type, typeTagKey, data.damage_indirect, data.duration_ticks);
                            success = true;
                        }
                    }
                }
                case VELOCITY -> {
                    var data = impact.action.velocity;
                    if (data != null) {
                        var push = data.push;
                        // The frame's horizontal +Z axis (unit, flat). For LOOK it is the caster's facing;
                        // for ORIGIN it points away from the impact's origin (the area-of-effect centre —
                        // explosion / cloud / meteor landing — or the caster for a direct hit). `push.y`
                        // is applied as world-up regardless of frame.
                        Vec3d zAxis;
                        switch (data.frame) {
                            case LOOK -> zAxis = Vec3d.fromPolar(0, caster.getYaw());
                            case ORIGIN -> {
                                var origin = context.hasOffset() ? context.position() : caster.getEntityPos();
                                var radial = new Vec3d(target.getX() - origin.x, 0, target.getZ() - origin.z);
                                // Target sits exactly on the origin (e.g. a self-cast): fall back to the
                                // caster's facing so the horizontal axes stay well-defined.
                                zAxis = radial.lengthSquared() < 1.0e-6
                                        ? Vec3d.fromPolar(0, caster.getYaw())
                                        : radial.normalize();
                            }
                            default -> zAxis = new Vec3d(0, 0, 1);
                        }
                        var xAxis = new Vec3d(-zAxis.z, 0, zAxis.x); // rightward horizontal, ⟂ to zAxis
                        var impulse = zAxis.multiply(push.z)
                                .add(xAxis.multiply(push.x))
                                .add(0, push.y, 0);
                        // Grow with the impact's spell power (deterministic — a launch shouldn't randomly
                        // vary the way crit-rolled damage does).
                        impulse = impulse.multiply(1.0 + data.power_coefficient * power.baseValue());
                        // A hostile shove is resisted by knockback resistance, just like a melee or
                        // explosion knock. A helpful launch (e.g. lifting yourself) ignores it.
                        if (data.intent == SpellTarget.Intent.HARMFUL && target instanceof LivingEntity livingTarget) {
                            var resistance = livingTarget.getAttributeValue(EntityAttributes.KNOCKBACK_RESISTANCE);
                            impulse = impulse.multiply(1.0 - resistance);
                        }
                        if (impulse.lengthSquared() > 0) {
                            ///
                            if (caster instanceof PlayerEntity player) {
                                SpellTriggers.onSpellImpactSpecific(player, target, spellEntry, impact, critical, Spell.Trigger.Stage.PRE);
                            }
                            ///
                            if (data.reset_velocity) {
                                target.setVelocity(Vec3d.ZERO);
                            }
                            target.addVelocity(impulse.x, impulse.y, impulse.z);
                            // Force a velocity sync to the entity's trackers.
                            target.velocityDirty = true;
                            // 1.21.11: the tracker only broadcasts velocity to *other* players (`sendToListeners`);
                            // the old `velocityModified → sendSyncPacket` path that also reached the moved player is
                            // gone. Player movement is client-authoritative, so the player's own client must be told
                            // explicitly — the same way vanilla knockback does it (PlayerEntity#attack).
                            if (target instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
                                net.spell_engine.Platform.util().sendPacket(serverPlayer, new net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket(serverPlayer));
                            }
                            success = true;
                        }
                    }
                }
                case SUMMON -> {
                    if (impact.action.summon != null) {
                        ///
                        if (caster instanceof PlayerEntity player) {
                            SpellTriggers.onSpellImpactSpecific(player, target, spellEntry, impact, critical, Spell.Trigger.Stage.PRE);
                        }
                        ///
                        summon(impact.action.summon, spellModifiers, spellEntry, caster, context);
                        success = true;
                    }
                }
                case CUSTOM -> {
                    if (impact.action.custom != null) {
                        var handler = SpellHandlers.customImpact.get(impact.action.custom.handler);
                        if (handler != null) {
                            ///
                            if (caster instanceof PlayerEntity player) {
                                SpellTriggers.onSpellImpactSpecific(player, target, spellEntry, impact, critical, Spell.Trigger.Stage.PRE);
                            }
                            ///
                            var result = handler.onSpellImpact(spellEntry, power, caster, target, context);
                            particleMultiplier = power.criticalDamage();
                            success = result.success();
                            critical = result.critical();
                        }
                    }
                }
            }
            if (success) {
                var impactVisuals = impact.visuals.resolved(Fx.Context.NONE);
                if (!impactVisuals.particles.isEmpty()) {
                    float countMultiplier = critical ? (float) particleMultiplier : 1F;
                    ParticleHelper.sendBatches(target, impactVisuals.particles, countMultiplier * caster.getScale(), trackers);
                }
                if (impact.sound != null) {
                    SoundHelper.playSound(world, target, impact.sound);
                }
                ModelEffectHelper.spawn(world, target.getEntityPos(), caster.getYaw(), impactVisuals.models,
                        target instanceof LivingEntity le ? le : null);
                if (targetWasAlive && caster instanceof PlayerEntity player) {
                    var finalTarget = target;
                    var finalCritical = critical;
                    ((WorldScheduler)world).schedule(0, () -> {
                        SpellTriggers.onSpellImpactSpecific(player, finalTarget, spellEntry, impact, finalCritical, Spell.Trigger.Stage.POST);
                    });
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to perform impact effect");
            System.err.println(e.getMessage());
            if (isKnockbackPushed) {
                ((ConfigurableKnockback)target).popKnockbackMultiplier_SpellEngine();
            }
        }
        return success;
    }

    public static boolean modifyCooldowns(List<RegistryEntry<Spell>> spells, Spell.Impact.Action.Cooldown.Modify modifier, SpellCooldownManager cooldownManager) {
        var modifiedAny = false;
        for (var spell: spells) {
            var id = spell.getKey().get().getValue();
            if (PatternMatching.matches(spell, SpellRegistry.KEY, modifier.id)) {
                var duration = cooldownManager.getCooldownDuration(spell);
                int updatedDuration = (int) ((duration + modifier.duration_add) * modifier.duration_multiplier);
                if (updatedDuration != duration) {
                    cooldownManager.setDurationLeft(spell, updatedDuration);
                    modifiedAny = true;
                }
            }
        }
        return modifiedAny;
    }

    // MARK: Conditions

    public static ConditionResult evaluateImpactConditions(Entity target, LivingEntity caster, List<Spell.Impact.TargetModifier> target_modifiers) {
        if (target_modifiers == null) {
            return ConditionResult.ALLOWED;
        }
        var modifiers = new ArrayList<Spell.Impact.Modifier>();
        for (var entry: target_modifiers) {
            var conditionMet = true;
            var i = 0;
            for (var condition: entry.conditions) {
                var newResult = SpellTarget.evaluate(target, caster, condition);
                if (i == 0) {
                    conditionMet = newResult;
                } else {
                    conditionMet = entry.all_required
                            ? conditionMet && newResult
                            : conditionMet || newResult;
                }
                i += 1;
            }
            switch (entry.execute) {
                case ALLOW -> {
                    if (!conditionMet) {
                        return ConditionResult.DENIED;
                    }
                }
                case DENY -> {
                    if (conditionMet) {
                        return ConditionResult.DENIED;
                    }
                }
            }
            if (conditionMet) {
                if (entry.modifier != null) {
                    modifiers.add(entry.modifier);
                }
            }
        }
        return new ConditionResult(true, modifiers);
    }

    public static boolean underApplyLimit(SpellPower.Result spellPower, LivingEntity target, SpellSchool school, Spell.Impact.Action.StatusEffect.ApplyLimit limit) {
        if (limit == null) {
            return true;
        }
        var power = (float) spellPower.nonCriticalValue();
        float cap = limit.health_base + (power * limit.spell_power_multiplier);
        return cap >= target.getMaxHealth();
    }

    // MARK: Summon

    /// Spawns the summon(s) from a {@link Spell.Impact.Action.Summon}. `group_count` groups are
    /// spawned; each group replays the per-entity formation (`spawn_count` entities cycling through
    /// `placements`), translated by the next group placement (cycling through `group_placements`).
    /// Every entity is created by id, handed the behaviour, positioned via
    /// {@link EntityPlacements#applyEntityPlacement} (group offset first, then the per-entity
    /// placement on top), and pulled back to the nearest visible point when a placement opts into
    /// line-of-sight. Group and per-entity `delay_ticks` are summed and defer the actual world spawn
    /// (entities are positioned at cast time, anchored to the caster's cast-time state, matching the
    /// built-in SPAWN action).
    private static void summon(Spell.Impact.Action.Summon def, List<Spell.Modifier> spellModifiers,
                               RegistryEntry<Spell> spellEntry, LivingEntity caster, ImpactContext context) {
        var world = caster.getEntityWorld();
        if (!(world instanceof ServerWorld serverWorld)) return;

        // Fold in summon-targeting spell modifiers without mutating the shared Summon / SummonBehaviour
        // instances stored on the registered spell: counts and lifespan adds are accumulated into
        // locals, attribute scaling and behaviour are recomputed onto fresh copies only when needed.
        int spawnCount = def.spawn_count;
        int groupCount = def.group_count;
        int spawnTicksAdd = 0, activeSecondsAdd = 0, despawnTicksAdd = 0;
        var extraActions = new ArrayList<SummonBehaviour.Action.Entry>();
        var attributeScaling = def.attribute_scaling;
        for (var modifier : spellModifiers) {
            spawnCount += modifier.summon_spawn_count_add;
            groupCount += modifier.summon_group_count_add;
            var behaviourMod = modifier.summon_behaviour;
            if (behaviourMod != null) {
                extraActions.addAll(behaviourMod.actions_add);
                spawnTicksAdd += behaviourMod.lifespan.spawn_ticks_add;
                activeSecondsAdd += behaviourMod.lifespan.active_seconds_add;
                despawnTicksAdd += behaviourMod.lifespan.despawn_ticks_add;
            }
            if (modifier.summon_attribute_scaling != null) {
                attributeScaling = AttributeScaling.merged(attributeScaling, modifier.summon_attribute_scaling);
            }
        }
        spawnCount = Math.max(0, spawnCount);
        groupCount = Math.max(0, groupCount);
        var behaviour = (extraActions.isEmpty() && spawnTicksAdd == 0 && activeSecondsAdd == 0 && despawnTicksAdd == 0)
                ? def.behaviour
                : def.behaviour.withModifiers(extraActions, spawnTicksAdd, activeSecondsAdd, despawnTicksAdd);

        var type = Registries.ENTITY_TYPE.get(Identifier.of(def.entity_type_id));
        for (int g = 0; g < groupCount; g++) {
            // Next group slot, wrapping around the list (null when no group offset is configured).
            var groupPlacement = def.group_placements.isEmpty() ? null : def.group_placements.get(g % def.group_placements.size());
            int groupDelay = groupPlacement != null ? groupPlacement.delay_ticks : 0;
            Vec3d groupAnchor = null; // caster position + group offset; captured from the first entity

            for (int i = 0; i < spawnCount; i++) {
                var created = (Entity) type.create(world, SpawnReason.MOB_SUMMONED);
                if (!(created instanceof SpellSummoned summoned)) return;

                // Next per-entity slot, wrapping around the list (null when no slots are configured).
                var placement = def.placements.isEmpty() ? null : def.placements.get(i % def.placements.size());

                summoned.onSummonedBySpell(new SpellSummoned.Args(caster, spellEntry, behaviour, attributeScaling, context));

                // Compose placements: the group offset's resulting position seeds the per-entity
                // placement (both rotate the look-offset by the caster's yaw, so the formation keeps
                // a consistent caster-relative orientation across groups).
                var origin = caster.getEntityPos();
                if (groupPlacement != null) {
                    EntityPlacements.applyEntityPlacement(created, caster, origin, groupPlacement);
                    origin = created.getEntityPos();
                }
                if (i == 0) groupAnchor = origin; // the group's anchor (pre per-entity offset)
                EntityPlacements.applyEntityPlacement(created, caster, origin, placement);

                // applyEntityPlacement only sets entity yaw; sync head/body yaw so the initial pose matches.
                boolean appliedYaw = (groupPlacement != null && groupPlacement.apply_yaw)
                        || (placement != null && placement.apply_yaw);
                if (appliedYaw && created instanceof LivingEntity living) {
                    living.setHeadYaw(living.getYaw());
                    living.setBodyYaw(living.getYaw());
                }
                // Anti-clip: when a contributing placement opts into `line_of_sight`, pull a placed
                // position that is out of the caster's line of sight back to the nearest visible point.
                boolean checkLineOfSight = (placement != null && placement.line_of_sight)
                        || (groupPlacement != null && groupPlacement.line_of_sight);
                if (checkLineOfSight) {
                    var visible = nearestVisiblePosition(caster, created.getEntityPos(), serverWorld);
                    created.setPosition(visible.x, visible.y, visible.z);
                }

                // Defer the world spawn by the combined group + per-entity delay (0 = spawn this tick).
                int entityDelay = placement != null ? placement.delay_ticks : 0;
                ((WorldScheduler) serverWorld).schedule(groupDelay + entityDelay, () -> serverWorld.spawnEntity(created));
            }

            // Group spawn FX + sound: one-shot at the group anchor, deferred by the group delay.
            if (groupAnchor != null && (def.group_spawn_fx != null || def.group_spawn_sound != null)) {
                var anchor = groupAnchor;
                var fx = def.group_spawn_fx;
                var sound = def.group_spawn_sound;
                ((WorldScheduler) serverWorld).schedule(groupDelay, () -> {
                    if (fx != null) emitSummonGroupFx(serverWorld, caster, anchor, fx);
                    if (sound != null) playSummonSound(serverWorld, anchor, sound);
                });
            }
        }
    }

    private static void emitSummonGroupFx(ServerWorld world, LivingEntity caster, Vec3d anchor, Fx.Visuals fx) {
        var visuals = fx.resolved(Fx.Context.NONE);
        if (!visuals.particles.isEmpty()) {
            ParticleHelper.sendBatches(anchor, caster, visuals.particles);
        }
        ModelEffectHelper.spawn(world, anchor, caster.getYaw(), visuals.models);
    }

    private static void playSummonSound(ServerWorld world, Vec3d pos, Sound sound) {
        var soundEvent = Registries.SOUND_EVENT.get(Identifier.of(sound.id()));
        if (soundEvent != null) {
            world.playSound(null, pos.x, pos.y, pos.z, soundEvent,
                    SoundCategory.PLAYERS, sound.volume(), sound.randomizedPitch());
        }
    }

    /// Distance the result is pulled back from a blocking surface along the sightline, so the entity
    /// sits just shy of the geometry rather than embedded in its face.
    private static final double SUMMON_LOS_BACKOFF = 0.5;

    /// The point along the segment from the caster's eyes to `desired` that is still in line of sight:
    /// `desired` itself when unobstructed, otherwise the closest clear point just before the blocking
    /// surface. Never returns a point behind the caster's eyes.
    private static Vec3d nearestVisiblePosition(LivingEntity caster, Vec3d desired, ServerWorld world) {
        var from = caster.getEyePos();
        var hit = world.raycast(new RaycastContext(
                from, desired,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                caster));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return desired; // unobstructed line of sight
        }
        var ray = desired.subtract(from);
        var length = ray.length();
        if (length < 1.0e-4) {
            return from;
        }
        var candidate = hit.getPos().subtract(ray.multiply(SUMMON_LOS_BACKOFF / length));
        // Guard against a surface right at the caster's face pushing the point behind the eyes.
        if (candidate.subtract(from).dotProduct(ray) < 0) {
            return from;
        }
        return candidate;
    }
}
