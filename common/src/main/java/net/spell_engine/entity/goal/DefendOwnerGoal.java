package net.spell_engine.entity.goal;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.spell_engine.entity.SummonedEntity;

import java.util.EnumSet;

/// Targets whoever attacked the owner (mirrors TrackOwnerAttackerGoal).
public class DefendOwnerGoal extends TargetGoal {
    private final SummonedEntity entity;
    private LivingEntity attacker;
    private int lastAttackedTime;

    public DefendOwnerGoal(SummonedEntity entity) {
        super(entity, false);
        this.entity = entity;
        setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        LivingEntity owner = entity.getOwner();
        if (owner == null) return false;
        attacker = owner.getLastHurtByMob();
        int time = owner.getLastHurtByMobTimestamp();
        return time != lastAttackedTime
                && canAttack(attacker, TargetingConditions.DEFAULT)
                && entity.canAttackTarget(attacker, owner);
    }

    @Override
    public void start() {
        entity.setTarget(attacker);
        LivingEntity owner = entity.getOwner();
        if (owner != null) lastAttackedTime = owner.getLastHurtByMobTimestamp();
        super.start();
    }
}
