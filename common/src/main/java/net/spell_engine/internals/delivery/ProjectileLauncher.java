package net.spell_engine.internals.delivery;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.event.SpellEvents;
import net.spell_engine.entity.SpellProjectile;
import net.spell_engine.internals.SpellExecution.ImpactContext;
import net.spell_engine.internals.SpellModifiers;
import net.spell_engine.utils.SoundHelper;
import net.spell_engine.utils.VectorHelper;
import net.spell_engine.utils.WorldScheduler;
import org.jetbrains.annotations.Nullable;

/// Launches the projectile-shaped deliveries: the PROJECTILE type, flying forward from the caster,
/// and the METEOR type, falling onto the target from above. Both apply spell modifiers to a mutable
/// copy of the authored launch properties and perks, then schedule any extra shots in the sequence.
public class ProjectileLauncher {

    // MARK: Projectile

    public static void shootProjectile(World world, LivingEntity caster, Entity target, RegistryEntry<Spell> spellEntry, ImpactContext context) {
        shootProjectile(world, caster, target, spellEntry, context, 0);
    }

    public static void shootProjectile(World world, LivingEntity caster, Entity target, RegistryEntry<Spell> spellEntry, ImpactContext context, int sequenceIndex) {
        if (world.isClient) {
            return;
        }

        var spell = spellEntry.value();
        var launchPoint = LaunchGeometry.launchPoint(caster);
        var data = spell.deliver.projectile;
        var projectileData = data.projectile;
        var mutablePerks = projectileData.perks.copy();
        var mutableLaunchProperties = data.launch_properties.copy();
        var scaleMultiplier = 1F;

        for (var modifier: SpellModifiers.of(caster, spellEntry, context.chargeModifier())) {
            if (modifier.projectile_launch != null) {
                mutableLaunchProperties.mutatingCombine(modifier.projectile_launch);
            }
            if (modifier.projectile_perks != null) {
                mutablePerks.mutatingCombine(modifier.projectile_perks);
            }
            scaleMultiplier += modifier.projectile_scale_multiply;
        }

        var effectiveCaster = context.effectiveCaster(world);
        var owner = effectiveCaster != null ? effectiveCaster : caster;

        var projectile = new SpellProjectile(world, owner,
                launchPoint.getX(), launchPoint.getY(), launchPoint.getZ(),
                SpellProjectile.Behaviour.FLY, spellEntry, context, mutablePerks);
        projectile.setScaleMultiplier(scaleMultiplier);

        if (SpellEvents.PROJECTILE_SHOOT.isListened()) {
            SpellEvents.PROJECTILE_SHOOT.invoke((listener) -> listener.onProjectileLaunch(
                    new SpellEvents.ProjectileLaunchEvent(projectile, mutableLaunchProperties, caster, target, spellEntry, context, sequenceIndex)));
        }
        var velocity = mutableLaunchProperties.velocity;
        var divergence = projectileData.divergence;
        var directionPitch = data.inherit_shooter_pitch ? caster.getPitch() : 0;
        var directionYaw = data.inherit_shooter_yaw ? caster.getYaw() : 0;
        if (data.direct_towards_target && target != null) {
            var directionVector = target.getPos().subtract(caster.getPos()).normalize();
            // Yaw and pitch from distance vector
            directionPitch = (float) VectorHelper.pitchFromNormalized(directionVector);
            directionYaw = (float) VectorHelper.yawFromNormalized(directionVector);
        }
        if (data.inherit_shooter_velocity) {
            projectile.setVelocity(caster, directionPitch, directionYaw, 0, velocity, divergence);
        } else {
            if (data.direction_offsets != null && data.direction_offsets.length > 0
                && (!data.direction_offsets_require_target || target != null)) {
                var baseIndex = context.isChanneled() ? context.channelTickIndex() : sequenceIndex;
                var index = baseIndex % data.direction_offsets.length;
                var offset = data.direction_offsets[index];
                directionPitch += offset.pitch;
                directionYaw += offset.yaw;
            }
            // var look = caster.getRotationVector().normalize();
            var look = caster.getRotationVector(directionPitch, directionYaw).normalize();
            projectile.setVelocity(look.x, look.y, look.z, velocity, divergence);
        }
        // Charge `bonus.range_add` extends the projectile's flight distance (already ratio-scaled).
        var chargeModifier = context.chargeModifier();
        projectile.range = spell.range + (chargeModifier != null ? chargeModifier.range_add : 0F);
        projectile.setPitch(directionPitch);
        projectile.setYaw(directionYaw);

        projectile.setFollowedTarget(target);
        world.spawnEntity(projectile);
        SoundHelper.playSound(world, projectile, mutableLaunchProperties.sound);

        var allowExtraShoot = (context.isChanneled() && mutableLaunchProperties.extra_launch_mod >= 0)
                ? context.channelTickIndex() % mutableLaunchProperties.extra_launch_mod == 0
                : true;
        if (sequenceIndex == 0 && mutableLaunchProperties.extra_launch_count > 0 && allowExtraShoot) {
            for (int i = 0; i < mutableLaunchProperties.extra_launch_count; i++) {
                var ticks = (i + 1) * mutableLaunchProperties.extra_launch_delay;
                var nextSequenceIndex = i + 1;
                ((WorldScheduler)world).schedule(ticks, () -> {
                    if (caster == null || !caster.isAlive()) {
                        return;
                    }
                    shootProjectile(world, caster, target, spellEntry, context, nextSequenceIndex);
                });
            }
        }
    }

    // MARK: Meteor

    public static boolean fallProjectile(World world, LivingEntity caster, Entity target, @Nullable Vec3d targetLocation, RegistryEntry<Spell> spellEntry, ImpactContext context) {
        return fallProjectile(world, caster, target, targetLocation, spellEntry, context, 0);
    }

    public static boolean fallProjectile(World world, LivingEntity caster, Entity target, @Nullable Vec3d targetLocation, RegistryEntry<Spell> spellEntry, ImpactContext context, int sequenceIndex) {
        if (world.isClient) {
            return false;
        }

        Vec3d targetPosition = (target != null) ? target.getPos() : targetLocation;
        if (targetPosition == null) {
            return false;
        }

        var spell = spellEntry.value();
        var meteor = spell.deliver.meteor;
        var height = meteor.launch_height;
        var launchPoint = targetPosition.add(0, height, 0);
        var data = spell.deliver.meteor;
        var projectileData = data.projectile;
        var mutableLaunchProperties = data.launch_properties.copy();
        var mutablePerks = projectileData.perks.copy();
        var scaleMultiplier = 1F;
        var launchRadius = meteor.launch_radius;

        for (var modifier: SpellModifiers.of(caster, spellEntry, context.chargeModifier())) {
            if (modifier.projectile_launch != null) {
                mutableLaunchProperties.mutatingCombine(modifier.projectile_launch);
            }
            if (modifier.projectile_perks != null) {
                mutablePerks.mutatingCombine(modifier.projectile_perks);
            }
            scaleMultiplier += modifier.projectile_scale_multiply;
            launchRadius += modifier.meteor_launch_radius_add;
        }

        var projectile = new SpellProjectile(world, caster,
                launchPoint.getX(), launchPoint.getY(), launchPoint.getZ(),
                SpellProjectile.Behaviour.FALL, spellEntry, context, mutablePerks);
        projectile.setScaleMultiplier(scaleMultiplier);

        if (SpellEvents.PROJECTILE_FALL.isListened()) {
            SpellEvents.PROJECTILE_FALL.invoke((listener) -> listener.onProjectileLaunch(new SpellEvents.ProjectileLaunchEvent(projectile, mutableLaunchProperties, caster, target, spellEntry, context, sequenceIndex)));
        }

        projectile.setYaw(0);
        projectile.setPitch(90);

        if (launchSequenceEligible(sequenceIndex, meteor.divergence_requires_sequence)) {
            projectile.setVelocity( 0, - 1, 0, mutableLaunchProperties.velocity, 0.5F, projectileData.divergence);
        } else {
            projectile.setVelocity(new Vec3d(0, - mutableLaunchProperties.velocity, 0));
        }
        if (launchSequenceEligible(sequenceIndex, meteor.follow_target_requires_sequence)) {
            projectile.setFollowedTarget(target);
        } else {
            projectile.setFollowedTarget(null);
        }
        if (launchRadius > 0 && launchSequenceEligible(sequenceIndex, meteor.offset_requires_sequence)) {
            var randomAngle = Math.toRadians(world.random.nextFloat() * 360);
            var offset = (new Vec3d(launchRadius, 0, 0)).rotateY((float) randomAngle);
            projectile.setPosition(projectile.getPos().add(offset));
        }

        projectile.prevYaw = projectile.getYaw();
        projectile.prevPitch = projectile.getPitch();
        projectile.range = height;

        world.spawnEntity(projectile);

        if (sequenceIndex == 0 && mutableLaunchProperties.extra_launch_count > 0) {
            for (int i = 0; i < mutableLaunchProperties.extra_launch_count; i++) {
                var ticks = (i + 1) * mutableLaunchProperties.extra_launch_delay;
                var nextSequenceIndex = i + 1;
                ((WorldScheduler)world).schedule(ticks, () -> {
                    if (caster == null || !caster.isAlive()) {
                        return;
                    }
                    fallProjectile(world, caster, target, targetLocation, spellEntry, context, nextSequenceIndex);
                });
            }
        }
        return true;
    }

    private static boolean launchSequenceEligible(int index, int rule) {
        if (rule == 0) {
            return false;
        }
        if (rule > 0) {
            return index >= rule;
        } else {
            return index < (-1 * rule);
        }
    }
}
