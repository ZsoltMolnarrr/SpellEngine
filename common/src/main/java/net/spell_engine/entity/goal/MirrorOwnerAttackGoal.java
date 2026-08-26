package net.spell_engine.entity.goal;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.spell_engine.entity.SummonedEntity;

import java.util.EnumSet;

/// Joins the owner's current attack target (mirrors AttackWithOwnerGoal) once the owner has hit it
/// `attack_with_owner_hits` times. The hit tally lives on the entity (see
/// SummonedEntity#getOwnerHitCount), so it keeps building even while this goal is committed to a
/// different target.
public class MirrorOwnerAttackGoal extends TargetGoal {
    private final SummonedEntity entity;
    private LivingEntity attacking;

    public MirrorOwnerAttackGoal(SummonedEntity entity) {
        super(entity, false);
        this.entity = entity;
        setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        LivingEntity owner = entity.getOwner();
        if (owner == null) return false;
        int threshold = Math.max(1, entity.behaviour != null ? entity.behaviour.targeting.attack_with_owner_hits : 1);
        if (entity.getOwnerHitTarget() == null || entity.getOwnerHitCount() < threshold) return false;
        attacking = entity.getOwnerHitTarget();
        return canAttack(attacking, TargetingConditions.DEFAULT)
                && entity.canAttackTarget(attacking, owner);
    }

    @Override
    public void start() {
        entity.setTarget(attacking);
        // Consume the tally so the goal doesn't immediately re-fire on the same hits.
        entity.consumeOwnerHits();
        super.start();
    }
}
