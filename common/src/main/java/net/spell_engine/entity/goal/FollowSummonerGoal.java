package net.spell_engine.entity.goal;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.Vec3d;
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
    @Nullable private Vec3d lastOwnerForward = null;

    public FollowSummonerGoal(SummonedEntity entity) {
        this.entity = entity;
        setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    private SummonBehaviour.Movement.Follow follow() {
        return entity.behaviour.movement.follow;
    }

    @Override
    public boolean canStart() {
        if (entity.hasLiveTarget()) return false;
        LivingEntity owner = entity.getOwner();
        if (owner == null) return false;
        float start = follow().start_distance;
        return entity.squaredDistanceTo(owner) > start * start;
    }

    @Override
    public boolean shouldContinue() {
        if (entity.hasLiveTarget()) return false;
        LivingEntity owner = entity.getOwner();
        if (owner == null) return false;
        float stop = follow().stop_distance;
        return entity.squaredDistanceTo(owner) > stop * stop;
    }

    @Override
    public void start() {
        LivingEntity owner = entity.getOwner();
        if (owner == null) return;
        Vec3d target = computeFollowTarget(owner);
        entity.getNavigation().startMovingTo(target.x, target.y, target.z, 1.0);
    }

    @Override
    public void tick() {
        LivingEntity owner = entity.getOwner();
        if (owner == null) return;
        Vec3d target = computeFollowTarget(owner);
        entity.getNavigation().startMovingTo(target.x, target.y, target.z, 1.0);
    }

    // Returns a position 2 blocks to the owner's right side. Forward is taken from the owner's
    // current velocity when significant; otherwise the last cached forward is reused. If no valid
    // forward has ever been observed, falls back to north.
    private Vec3d computeFollowTarget(LivingEntity owner) {
        Vec3d vel = owner.getVelocity();
        double horizSpeed = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        if (horizSpeed > 0.02) {
            lastOwnerForward = new Vec3d(vel.x / horizSpeed, 0, vel.z / horizSpeed);
        }
        // North (-Z) as the last-resort default before the owner has ever moved
        Vec3d forward = lastOwnerForward != null ? lastOwnerForward : new Vec3d(0, 0, -1);
        // Right = forward rotated 90° clockwise (viewed from above) in Minecraft's coordinate system
        Vec3d right = new Vec3d(-forward.z, 0, forward.x);
        return owner.getEntityPos().add(right.multiply(2.0));
    }
}
