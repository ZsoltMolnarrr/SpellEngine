package net.spell_engine.entity.goal;

import net.spell_engine.internals.target.EntityRelations;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.spell_engine.api.spell.summon.SummonBehaviour;
import net.spell_engine.entity.SummonedEntity;

import java.util.EnumSet;

// Windup-based melee attack. Each swing has a fixed duration; impact lands at `windup`
// ticks into the swing. Movement speed is multiplied by `movement_modifier` while
// swinging. If the target leaves attack reach before the impact tick, the swing fails
// silently (no damage). Optional AoE: on a successful impact, all valid hostiles within
// `radius` of the primary target are also struck.
public class DynamicMeleeAttackGoal extends Goal {
    // Once a swing has begun, the target can drift up to this multiplier of the normal
    // attack reach (and configured max_range) before the impact-tick check whiffs.
    // Keeps the swing from being instantly cancelled by sub-block target jitter, while
    // the strict (1.0×) range still gates whether a new swing can start.
    private static final float IN_PROGRESS_RANGE_TOLERANCE = 1.10F;

    private final SummonedEntity summonedEntity;
    private final SummonBehaviour.Action.MeleeAttack config;
    private int swingTick = -1; // -1 = not swinging
    private int navUpdateCountdown = 0;

    public DynamicMeleeAttackGoal(SummonedEntity summonedEntity, SummonBehaviour.Action.MeleeAttack config) {
        this.summonedEntity = summonedEntity;
        this.config = config;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    // Approximation of vanilla MeleeAttackGoal's reach (entity width + target width).
    private double squaredAttackReach(LivingEntity target) {
        float reach = summonedEntity.getBbWidth() * 2.0F + target.getBbWidth();
        return reach * reach;
    }

    private boolean isTargetInRange(LivingEntity target) {
        return isTargetInRange(target, 1.0F);
    }

    private boolean isTargetInRange(LivingEntity target, float toleranceMultiplier) {
        double sq = summonedEntity.distanceToSqr(target);
        double tolSq = toleranceMultiplier * toleranceMultiplier;
        if (sq > squaredAttackReach(target) * tolSq) return false;
        if (config.max_range > 0) {
            float r = effectiveMaxRange();
            if (sq > r * r * tolSq) return false;
        }
        return true;
    }

    // max_range expanded by the entity's scale. At default scale 1.0 and default
    // attack_range_scaling 0.5, this is 1.5 × max_range.
    // `getScale()` is the GENERIC_SCALE attribute (what summon behaviours actually
    // modify); `getScaleFactor()` is vanilla's baby-only multiplier (0.5/1.0) and
    // would never grow with the configured scaling.
    private float effectiveMaxRange() {
        return (float) (config.max_range * (1 + summonedEntity.getScale() * config.attack_range_scaling));
    }

    // Distance at which navigation holds rather than pursuing further. Held back from full
    // attack reach so the entity doesn't bump into the target. Directional bias mirrors
    // SpellCastGoal:
    //   target fleeing     → close in     (50% of reach)
    //   target approaching → hold back    (90%)
    //   stationary         → moderate     (70%)
    private double squaredHoldRange(LivingEntity target) {
        double sqMax = squaredAttackReach(target);
        if (config.max_range > 0) {
            float r = effectiveMaxRange();
            sqMax = Math.min(sqMax, (double) r * r);
        }
        Vec3 toTarget = target.position().subtract(summonedEntity.position()).normalize();
        double dot = target.getDeltaMovement().dot(toTarget);
        float frac = (dot > 0.01) ? 0.5f : (dot < -0.01) ? 0.9f : 0.7f;
        return sqMax * frac * frac;
    }

    // Cooldown between consecutive swings, in ticks (derived from attack speed alone).
    // Overlap with an in-progress swing is prevented separately by the `swingTick < 0` gate.
    private int swingInterval() {
        if (config.speed <= 0) return 1;
        return Math.max(1, Math.round(20F / config.speed));
    }

    // Tick within the swing at which the impact lands.
    // `windup` is a 0..1 fraction of `duration`; clamped so the impact never
    // falls outside the swing window.
    private int windupTick() {
        float f = Math.max(0F, Math.min(1F, config.windup));
        return Math.min(config.duration - 1, Math.round(config.duration * f));
    }

    // Mirrors PlayerEntity.getAttackCooldownProgress: 1.0F means fully recovered.
    private float attackCooldownProgress() {
        int interval = swingInterval();
        int ticksSince = summonedEntity.tickCount - summonedEntity.getLastHurtMobTimestamp();
        return Math.min(1F, ticksSince / (float) interval);
    }

    @Override
    public boolean canUse() {
        if (!summonedEntity.isActive()) return false;
        LivingEntity target = summonedEntity.getTarget();
        if (target == null || !target.isAlive()) return false;
        // If a max_range cap is set, don't engage targets outside it (no chase).
        if (config.max_range > 0) {
            float r = effectiveMaxRange();
            if (summonedEntity.distanceToSqr(target) > r * r) return false;
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (swingTick >= 0) return true; // finish in-progress swing
        return canUse();
    }

    // Block preemption by higher-priority goals (spell casts) while a swing is in progress.
    // GoalSelector replaces a running goal only when the current holder's canStop() is true
    // (PrioritizedGoal.canBeReplacedBy). Without this override, a spell goal whose cooldown
    // expires mid-windup steals MOVE/LOOK from this goal, stop() runs (swingTick = -1), and
    // the impact-tick branch never fires — the windup animation plays out, but tryAttack is
    // never called and the swing silently whiffs regardless of target distance.
    @Override
    public boolean isInterruptable() {
        return swingTick < 0;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        swingTick = -1;
        navUpdateCountdown = 0;
    }

    @Override
    public void stop() {
        swingTick = -1;
        summonedEntity.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity target = summonedEntity.getTarget();
        // If the target vanished mid-swing, abort the swing so shouldContinue() returns false
        // next tick and MOVE/LOOK get released — otherwise FollowSummonerGoal can never start.
        if (target == null || !target.isAlive()) {
            swingTick = -1;
            return;
        }
        // Lock onto the target — every tick the melee goal is active, the entity faces
        // its target without the 30°/tick smoothing lag of `lookAt`.
        summonedEntity.lockRotationTo(target);

        boolean inRange = isTargetInRange(target);

        // Begin a new swing only when not currently swinging, in range, and fully recovered.
        // Recovery uses vanilla LivingEntity.lastAttackTime: onAttacking(target) below sets it
        // to the current age, mirroring how PlayerEntity.getAttackCooldownProgress works.
        if (swingTick < 0 && inRange && attackCooldownProgress() >= 1F) {
            swingTick = 0;
            summonedEntity.setLastHurtMob(target); // saves vanilla lastAttackTime = age
            summonedEntity.onAttackAnimated(config.duration, summonedEntity.pickVariant(config.animation_variants));
            summonedEntity.playConfiguredSound(config.swing_sound);
//            SummonedEntity.LOGGER.info("[WindupMelee] attack-start entity={} target={} duration={} windup={}->tick{} radius={} interval={}",
//                    summonedEntity.getId(), target.getId(),
//                    config.duration, config.windup, windupTick(), config.radius, swingInterval());
        }

        // Navigation: hold inside the (smaller) desired range — held back from full
        // attack reach so the entity doesn't bump into the target. Pursue otherwise,
        // slowed to movement_modifier × movement_speed during a swing.
        boolean atHoldRange = summonedEntity.distanceToSqr(target) <= squaredHoldRange(target);
        double moveSpeed = (swingTick >= 0)
                ? config.movement_speed * config.movement_modifier
                : config.movement_speed;
        navUpdateCountdown--;
        if (atHoldRange) {
            summonedEntity.getNavigation().stop();
        } else if (moveSpeed > 0) {
            if (navUpdateCountdown <= 0) {
                summonedEntity.getNavigation().moveTo(target, moveSpeed);
                navUpdateCountdown = 5;
            }
        } else {
            summonedEntity.getNavigation().stop();
        }

        // Drive swing progression.
        if (swingTick >= 0) {
            if (swingTick == windupTick()) {
                // Re-check reach with in-progress tolerance: if the target drifted only
                // slightly out during the windup the swing still connects; only a clear
                // dodge whiffs it.
                boolean hit = isTargetInRange(target, IN_PROGRESS_RANGE_TOLERANCE);
//                SummonedEntity.LOGGER.info("[WindupMelee] attack-impact entity={} target={} tick={} hit={}",
//                        summonedEntity.getId(), target.getId(), swingTick, hit);
                if (hit) {
                    performAttackImpact(target);
                }
            }
            swingTick++;
            if (swingTick >= config.duration) {
                swingTick = -1;
                // No explicit ATTACK_ANIMATION stop needed: the client auto-stops the
                // animation when `age - startAge >= duration`. Every new swing carries a
                // fresh monotonic startAge in the packed tracker, so back-to-back swings
                // always re-sync even if variant and duration repeat.
                summonedEntity.onActionCompleted(SummonBehaviour.Action.Type.MELEE_ATTACK, null);
            }
        }
    }

    private void performAttackImpact(LivingEntity primary) {
        // Played once per swing, before any tryAttack calls — AoE hits do not retrigger it.
        summonedEntity.playConfiguredSound(config.impact_sound);
        var serverWorld = (ServerLevel) summonedEntity.level();
        summonedEntity.doHurtTarget(serverWorld, primary);
        if (config.radius <= 0) return;
        // Scale the AoE radius with the entity's GENERIC_SCALE attribute so a larger
        // summon hits a proportionally larger area — keeps the impact footprint visually
        // consistent with the bigger model and the already-scaled melee reach.
        float radius = config.radius * summonedEntity.getScale();
        LivingEntity owner = summonedEntity.getOwner();
        AABB box = primary.getBoundingBox().inflate(radius);
        double radiusSq = (double) radius * radius;
        for (LivingEntity nearby : summonedEntity.level().getEntitiesOfClass(LivingEntity.class, box, e -> true)) {
            if (nearby == primary) continue;
            if (nearby == summonedEntity) continue;
            if (nearby == owner) continue;
            if (nearby instanceof OwnableEntity t && owner != null && owner.getUUID().equals(EntityRelations.ownerUuid(t))) continue;
            if (owner != null && !summonedEntity.canAttackTarget(nearby, owner)) continue;
            if (nearby.distanceToSqr(primary) > radiusSq) continue;
            summonedEntity.doHurtTarget(serverWorld, nearby);
        }
    }

}
