package net.spell_engine.entity.goal;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.spell_engine.api.spell.summon.SummonBehaviour;
import net.spell_engine.entity.SummonedEntity;

import java.util.EnumSet;

/// One-shot teleport to the owner when they are farther than `teleport_after_distance`. Runs at a
/// higher priority than action goals and claims Control.MOVE, so it preempts an in-progress combat
/// goal. The target is cleared after the teleport so the summon doesn't immediately sprint back.
public class TeleportToSummonerGoal extends Goal {
    private final SummonedEntity entity;

    public TeleportToSummonerGoal(SummonedEntity entity) {
        this.entity = entity;
        setControls(EnumSet.of(Control.MOVE));
    }

    private SummonBehaviour.Movement.Follow follow() {
        return entity.behaviour.movement.follow;
    }

    @Override
    public boolean canStart() {
        LivingEntity owner = entity.getOwner();
        if (owner == null) return false;
        float teleportDist = follow().teleport_after_distance;
        if (teleportDist <= 0) return false;
        return entity.squaredDistanceTo(owner) > teleportDist * teleportDist;
    }

    @Override
    public boolean shouldContinue() { return false; }

    @Override
    public void start() {
        LivingEntity owner = entity.getOwner();
        if (owner == null) return;
        entity.teleport(owner.getX(), owner.getY(), owner.getZ(), false);
        entity.setTarget(null);
        entity.getNavigation().stop();
    }
}
