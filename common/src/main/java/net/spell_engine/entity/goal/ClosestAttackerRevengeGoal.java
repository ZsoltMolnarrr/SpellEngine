package net.spell_engine.entity.goal;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.entity.ai.goal.TrackTargetGoal;
import net.spell_engine.entity.SummonedEntity;

import java.util.EnumSet;

/// Revenge that prefers the nearer threat. Unlike vanilla RevengeGoal — which always switches to
/// whoever landed the last hit — this only retargets when the new attacker is closer than the
/// current target, so the summon stays focused on whatever enemy is in its face.
///
/// Retaliation is gated on {@link SummonedEntity#canAttackTarget} (HOSTILE / NEUTRAL only), the same
/// predicate DefendOwnerGoal and MirrorOwnerAttackGoal apply. Without it, *any* source of damage
/// became a target — most visibly the owner themselves, who only has to clip their own summon with a
/// stray AoE or melee swing to have it turn on them. ALLY and FRIENDLY attackers (the owner, the
/// owner's other pets, teammates) can no longer be revenge targets.
public class ClosestAttackerRevengeGoal extends TrackTargetGoal {
    private final SummonedEntity entity;
    private int lastAttackedTime;
    // The attacker canStart() validated, carried into start() so the target actually set is the one
    // that passed the relation check (mirrors DefendOwnerGoal), rather than a re-read of getAttacker().
    private LivingEntity attacker;

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
        this.attacker = attacker;
        LivingEntity owner = entity.getOwner();
        // Relation is resolved from the owner's perspective, so an ownerless summon (owner offline)
        // has no basis to classify its attacker and does not retaliate at all — matching how the
        // other target-selector goals behave without an owner.
        if (owner == null) return false;
        if (!entity.canAttackTarget(attacker, owner)) {
            // Friendly fire: consume the hit so canStart doesn't re-evaluate it every tick.
            lastAttackedTime = time;
            return false;
        }
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
        entity.setTarget(attacker);
        lastAttackedTime = entity.getLastAttackedTime();
        super.start();
    }
}
