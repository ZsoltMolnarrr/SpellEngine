package net.spell_engine.entity.goal;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.entity.ai.goal.TrackTargetGoal;
import net.spell_engine.entity.SummonedEntity;

import java.util.EnumSet;

/// Targets whoever attacked the owner (mirrors TrackOwnerAttackerGoal).
public class DefendOwnerGoal extends TrackTargetGoal {
    private final SummonedEntity entity;
    private LivingEntity attacker;
    private int lastAttackedTime;

    public DefendOwnerGoal(SummonedEntity entity) {
        super(entity, false);
        this.entity = entity;
        setControls(EnumSet.of(Control.TARGET));
    }

    @Override
    public boolean canStart() {
        LivingEntity owner = entity.getOwner();
        if (owner == null) return false;
        attacker = owner.getAttacker();
        int time = owner.getLastAttackedTime();
        return time != lastAttackedTime
                && canTrack(attacker, TargetPredicate.DEFAULT)
                && entity.canAttackTarget(attacker, owner);
    }

    @Override
    public void start() {
        entity.setTarget(attacker);
        LivingEntity owner = entity.getOwner();
        if (owner != null) lastAttackedTime = owner.getLastAttackedTime();
        super.start();
    }
}
