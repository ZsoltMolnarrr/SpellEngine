package net.spell_engine.entity.goal;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.entity.ai.goal.TrackTargetGoal;
import net.spell_engine.entity.SummonedEntity;

import java.util.EnumSet;

/// Revenge that prefers the nearer threat. Unlike vanilla RevengeGoal — which always switches to
/// whoever landed the last hit — this only retargets when the new attacker is closer than the
/// current target, so the summon stays focused on whatever enemy is in its face.
public class ClosestAttackerRevengeGoal extends TrackTargetGoal {
    private final SummonedEntity entity;
    private int lastAttackedTime;

    public ClosestAttackerRevengeGoal(SummonedEntity entity) {
        super(entity, true);
        this.entity = entity;
        setControls(EnumSet.of(Control.TARGET));
    }

    @Override
    public boolean canStart() {
        int time = entity.getLastAttackedTime();
        if (time == lastAttackedTime) return false;
        LivingEntity attacker = entity.getAttacker();
        if (attacker == null) return false;
        if (!canTrack(attacker, TargetPredicate.DEFAULT)) return false;
        LivingEntity currentTarget = entity.getTarget();
        if (currentTarget != null && currentTarget.isAlive()
                && entity.squaredDistanceTo(attacker) >= entity.squaredDistanceTo(currentTarget)) {
            // Attacker isn't closer — consume the hit so canStart doesn't re-fire every tick.
            lastAttackedTime = time;
            return false;
        }
        return true;
    }

    @Override
    public void start() {
        entity.setTarget(entity.getAttacker());
        lastAttackedTime = entity.getLastAttackedTime();
        super.start();
    }
}
