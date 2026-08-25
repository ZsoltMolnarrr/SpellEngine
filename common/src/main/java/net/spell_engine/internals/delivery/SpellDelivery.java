package net.spell_engine.internals.delivery;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.spell_engine.Platform;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.event.SpellHandlers;
import net.spell_engine.internals.SpellExecution.DeliveryCompletion;
import net.spell_engine.internals.SpellExecution.DeliveryTarget;
import net.spell_engine.internals.SpellExecution.ImpactContext;
import net.spell_engine.internals.impact.SpellImpacts;
import net.spell_engine.internals.SpellModifiers;
import net.spell_engine.internals.SpellParameters;
import net.spell_engine.internals.delivery.arrow.ArrowHelper;
import net.spell_engine.internals.casting.SpellCaster;
import net.spell_engine.internals.delivery.melee.Melee;
import net.spell_engine.internals.target.SpellTarget;
import net.spell_engine.network.Packets;
import net.spell_engine.utils.TargetHelper;
import net.spell_engine.utils.WorldScheduler;
import net.spell_power.api.SpellPower;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/// The DELIVER stage: how a spell travels from a resolved set of targets to the point where its
/// impacts run. {@link #resolveAndDeliver} turns a targeting result into per-target contexts,
/// {@link #deliver} dispatches on the delivery type — some types impact immediately, others hand
/// off to a projectile, cloud or client-side melee swing that impacts later.
public class SpellDelivery {

    /// Routes a resolved {@link SpellTarget.SearchResult} through the delivery system based on the
    /// spell's targeting type. Called from both the player cast path and the entity cast path
    /// (see {@link net.spell_engine.internals.casting.SpellCasting}).
    public static boolean resolveAndDeliver(
            World world,
            LivingEntity caster,
            RegistryEntry<Spell> spellEntry,
            SpellTarget.SearchResult targetResult,
            ImpactContext context,
            @Nullable Consumer<DeliveryCompletion> completion) {
        var spell = spellEntry.value();
        var targeting = spell.target;
        var targets = targetResult.entities();
        boolean success = false;
        switch (targeting.type) {
            case NONE -> {
                success = deliver(world, spellEntry, caster, List.of(), context, null, completion);
            }
            case CASTER -> {
                var targetsWithContext = List.of(new DeliveryTarget(caster, context));
                success = deliver(world, spellEntry, caster, targetsWithContext, context, null, completion);
            }
            case AIM -> {
                var aim = targeting.aim;
                var firstTarget = targets.stream().findFirst();
                List<DeliveryTarget> targetsWithContext = List.of();
                if (firstTarget.isPresent()) {
                    var target = firstTarget.get();
                    targetsWithContext = List.of(new DeliveryTarget(target, context));
                }
                if (!aim.required || firstTarget.isPresent()) {
                    var location = targetResult.location();
                    if (location != null && firstTarget.isEmpty() && aim.reposition_vertically != 0) {
                        var collidedLocation = TargetHelper.findSolidBelow(caster, location, world, aim.reposition_vertically);
                        if (collidedLocation != null) {
                            location = collidedLocation;
                        }
                    }
                    success = deliver(world, spellEntry, caster, targetsWithContext, context, location, completion);
                }
                // Very specific attempt failure display, generic solution would be very difficult
                if (!success && aim.required && firstTarget.isEmpty()) {
                    if (caster instanceof ServerPlayerEntity serverPlayer) {
                        Platform.util().networkS2C_Send(serverPlayer, new Packets.SpellMessage("hud.cast_attempt_error.missing_target", Formatting.RED));
                    }
                }
            }
            case AREA -> {
                var center = caster.getEntityPos().add(0, caster.getHeight() / 2F, 0);
                var area = spell.target.area;
                var range = SpellParameters.getRangeCurved(caster, spellEntry, context.charge()) * caster.getScale();
                final var centeredContext = context; // .position(center);
                double squaredRange = range * range;
                var targetsWithContext = targets.stream().map(target -> {
                    float distanceBasedMultiplier = 1F;
                    switch (area.distance_dropoff) {
                        case NONE -> { }
                        case SQUARED -> {
                            distanceBasedMultiplier = (float) ((squaredRange - target.squaredDistanceTo(center)) / squaredRange);
                            distanceBasedMultiplier = Math.max(distanceBasedMultiplier, 0F);
                        }
                    }
                    return new DeliveryTarget(target, centeredContext.distance(distanceBasedMultiplier));
                }).toList();
                // `forceSuccess` is true because area spells should always go to cooldown
                deliver(world, spellEntry, caster, targetsWithContext, context, null, completion, true, false);
                // success = true; // Always true, otherwise area spells don't go to CD without targets
            }
            case BEAM -> {
                var targetsWithContext = targets.stream().map(target -> new DeliveryTarget(target, context)).toList();
                success = deliver(world, spellEntry, caster, targetsWithContext, context, null, completion);
            }
            case FROM_TRIGGER -> {
                var targetsWithContext = targets.stream().map(target -> new DeliveryTarget(target, context)).toList();
                success = deliver(world, spellEntry, caster, targetsWithContext, context, targetResult.location(), completion);
            }
            default -> throw new IllegalStateException("Unexpected value: " + targeting.type);
        }
        return success;
    }

    public static boolean deliver(World world, RegistryEntry<Spell> spellEntry, LivingEntity caster, List<DeliveryTarget> targets, ImpactContext context, @Nullable Vec3d targetLocation, Consumer<DeliveryCompletion> completion) {
        return deliver(world, spellEntry, caster, targets, context, targetLocation, completion, false, false);
    }

    public static boolean deliver(World world, RegistryEntry<Spell> spellEntry, LivingEntity caster, List<DeliveryTarget> targets, ImpactContext context,
                                  @Nullable Vec3d targetLocation, @Nullable Consumer<DeliveryCompletion> completion, boolean forceSuccess, boolean scheduled) {
        var spell = spellEntry.value();

        if (spell.deliver.delay > 0) {
            if (scheduled) {
                Predicate<Entity> validator = (entity) -> !(entity == null || entity.isRemoved());
                if (!validator.test(caster)) {
                    return false;
                }
                targets = targets.stream().filter(target -> validator.test(target.entity())).toList();
            } else {
                List<DeliveryTarget> finalTargets = targets;
                ((WorldScheduler) world).schedule(spell.deliver.delay, () -> deliver(world, spellEntry, caster, finalTargets, context, targetLocation, completion, forceSuccess, true));
                return true;
            }
        }

        var delivered = false;
        switch (spell.deliver.type) {
            case DIRECT -> {
                var anySuccess = false;
                var casterPos = caster.getEntityPos().add(0, caster.getHeight() / 2F, 0);
                if (targets.isEmpty() && targetLocation != null
                        && spell.area_impact != null) { // Special check to allow area impacts only, in the absence of targets
                    var position = targetLocation.lerp(casterPos, 0.001F);
                    var targetSpecificContext = context.position(position);
                    SpellImpacts.performImpacts(world, caster, caster, null, spellEntry, spell.impacts, targetSpecificContext);
                    anySuccess = true; // The area impact will be executed, hence always true
                } else {
                    for(var targeted: targets) {
                        var target = targeted.entity();
                        var position = target == caster
                                ? casterPos
                                : target.getEntityPos().add(0, target.getHeight() / 2F, 0).lerp(casterPos, 0.01F);
                        var targetSpecificContext = targeted.context().position(position);
                        var result = SpellImpacts.performImpacts(world, caster, target, target, spellEntry, spell.impacts, targetSpecificContext);
                        anySuccess = anySuccess || result;
                    }
                }
                delivered = anySuccess;
            }
            case PROJECTILE -> {
                if (targets.isEmpty()) {
                    ProjectileLauncher.shootProjectile(world, caster, null, spellEntry, context);
                } else {
                    for(var targeted: targets) {
                        var target = targeted.entity();
                        var targetSpecificContext = targeted.context();
                        ProjectileLauncher.shootProjectile(world, caster, target, spellEntry, targetSpecificContext);
                    }
                }
                delivered = true;
            }
            case METEOR -> {
                var anyLaunched = false;
                if (targets.isEmpty() && targetLocation != null) {
                    ProjectileLauncher.fallProjectile(world, caster, null, targetLocation, spellEntry, context);
                    anyLaunched = true;
                } else {
                    for(var targeted: targets) {
                        var target = targeted.entity();
                        var targetSpecificContext = targeted.context();
                        ProjectileLauncher.fallProjectile(world, caster, target, null, spellEntry, targetSpecificContext);
                        anyLaunched = true;
                    }
                }
                delivered = anyLaunched;
            }
            case CLOUD -> {
                var placedAny = false;
                if (targets.isEmpty() && targetLocation != null) {
                    CloudPlacer.placeCloud(world, caster, null, targetLocation, spellEntry, context.position(targetLocation));
                    placedAny = true;
                } else {
                    for(var targeted: targets) {
                        var target = targeted.entity();
                        var targetSpecificContext = targeted.context();
                        CloudPlacer.placeCloud(world, caster, target, null, spellEntry, targetSpecificContext);
                        placedAny = true;
                    }
                }
                delivered = placedAny;
            }
            case SHOOT_ARROW -> {
                ArrowHelper.shootArrow(world, caster, spellEntry, context);
                delivered = true;
            }
            case AFFECT_ARROW -> {
                if (caster instanceof SpellCaster.Player shooter) {
                    var arrowContext = shooter.getArrowShootContext();
                    arrowContext.activeSpells.add(spellEntry);
                }
                delivered = true;
            }
            case MELEE -> {
                if (spell.deliver.melee != null
                        && !spell.deliver.melee.attacks.isEmpty()) {
                    var attackers = !targets.isEmpty()
                            ? targets.stream().map(e -> e.entity()).toList()
                            : List.of(caster);
                    var meleeData = spell.deliver.melee;
                    var spellId = spellEntry.getKey().get().getValue();
                    var attacks = meleeData.attacks;
                    if (context.isChanneled()) {
                        var index = context.channelTickIndex() % attacks.size();
                        attacks = List.of(attacks.get(index));
                    }
                    for (var attacker: attackers) {
                        if (!attacker.isOnGround() && !spell.deliver.melee.allow_airborne) {
                            break;
                        }
                        if (attacker instanceof ServerPlayerEntity serverPlayer) {
                            // Map to resolved MeleeAttack structures
                            var meleeAttacks = Melee.createMeleeAttacks(serverPlayer, attacks, spellEntry,
                                    context.charge(), context.chargeModifier());
                            // Send AttackAvailable packet to client
                            var packet = new Packets.AttackAvailable(spellId, meleeAttacks);
                            Platform.util().networkS2C_Send(serverPlayer, packet);
                            delivered = true;
                        }
                    }
                }
            }
            case STASH_EFFECT -> {
                var anyAdded = false;
                var stash = spell.deliver.stash_effect;
                var id = Identifier.of(stash.id);
                var effect = Registries.STATUS_EFFECT.getEntry(id).get();

                var amplifier = stash.amplifier;
                if (stash.amplifier_power_multiplier != 0) {
                    var power = SpellPower.getSpellPower(spell.school, caster);
                    amplifier += (int)(stash.amplifier_power_multiplier * power.nonCriticalValue());
                }
                for (var modifier: SpellModifiers.of(caster, spellEntry, context.chargeModifier())) {
                    amplifier += modifier.stash_amplifier_add;
                }
                for (var targeted: targets) {
                    if (targeted.entity() instanceof LivingEntity livingEntity) {
                        if (stash.stacking) {
                            var stack = -1;
                            var existingInstance = livingEntity.getStatusEffect(effect);
                            if (existingInstance != null) {
                                stack = existingInstance.getAmplifier();
                                livingEntity.removeStatusEffect(effect);
                            }
                            stack += 1;
                            var instance = new StatusEffectInstance(effect, (int) (stash.duration * 20), Math.min(stack, amplifier), false, stash.show_particles, true);
                            livingEntity.addStatusEffect(instance);
                        } else {
                            var instance = new StatusEffectInstance(effect, (int) (stash.duration * 20), amplifier, false, stash.show_particles, true);
                            livingEntity.addStatusEffect(instance);
                        }
                        anyAdded = true;
                    }
                }
                delivered = anyAdded;
            }
            case CUSTOM -> {
                if (spell.deliver.custom != null) {
                    var handler = SpellHandlers.customDelivery.get(spell.deliver.custom.handler);
                    if (handler != null) {
                        delivered = handler.onSpellDelivery(world, spellEntry, caster, targets, context, targetLocation);
                    }
                }
            }
        }

        if (completion != null) {
            completion.accept(new DeliveryCompletion(delivered || forceSuccess));
        }

        return delivered;
    }
}
