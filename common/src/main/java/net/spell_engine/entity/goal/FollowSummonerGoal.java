package net.spell_engine.entity.goal;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import net.spell_engine.api.spell.summon.SummonBehaviour;
import net.spell_engine.entity.SummonedEntity;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/// Walks the summon to a position beside the owner when it falls behind, deferring to combat the
/// moment a target is acquired.
public class FollowSummonerGoal extends Goal {
    private final SummonedEntity entity;
    // Last horizontal velocity of the owner significant enough to determine orientation.
    // Null until the owner is seen moving; falls back to north when still null.
    @Nullable private Vec3 lastOwnerForward = null;

    public FollowSummonerGoal(SummonedEntity entity) {
        this.entity = entity;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private SummonBehaviour.Movement.Follow follow() {
        return entity.behaviour.movement.follow;
    }

    @Override
    public boolean canUse() {
        if (entity.hasLiveTarget()) return false;
        LivingEntity owner = entity.getOwner();
        if (owner == null) return false;
        float start = follow().start_distance;
        return entity.distanceToSqr(owner) > start * start;
    }

    @Override
    public boolean canContinueToUse() {
        if (entity.hasLiveTarget()) return false;
        LivingEntity owner = entity.getOwner();
        if (owner == null) return false;
        float stop = follow().stop_distance;
        return entity.distanceToSqr(owner) > stop * stop;
    }

    @Override
    public void start() {
        LivingEntity owner = entity.getOwner();
        if (owner == null) return;
        Vec3 target = computeFollowTarget(owner);
        entity.getNavigation().moveTo(target.x, target.y, target.z, 1.0);
    }

    @Override
    public void tick() {
        LivingEntity owner = entity.getOwner();
        if (owner == null) return;
        Vec3 target = computeFollowTarget(owner);
        entity.getNavigation().moveTo(target.x, target.y, target.z, 1.0);
    }

    // Returns a position 2 blocks to the owner's right side. Forward is taken from the owner's
    // current velocity when significant; otherwise the last cached forward is reused. If no valid
    // forward has ever been observed, falls back to north.
    private Vec3 computeFollowTarget(LivingEntity owner) {
        Vec3 vel = owner.getDeltaMovement();
        double horizSpeed = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        if (horizSpeed > 0.02) {
            lastOwnerForward = new Vec3(vel.x / horizSpeed, 0, vel.z / horizSpeed);
        }
        // North (-Z) as the last-resort default before the owner has ever moved
        Vec3 forward = lastOwnerForward != null ? lastOwnerForward : new Vec3(0, 0, -1);
        // Right = forward rotated 90° clockwise (viewed from above) in Minecraft's coordinate system
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        return owner.position().add(right.scale(2.0));
    }
}
