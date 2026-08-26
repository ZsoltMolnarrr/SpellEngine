package net.spell_engine.entity.goal;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.spell_engine.entity.SummonedEntity;

import java.util.EnumSet;

/// Holds LOOK control whenever the entity has an attack target, keeping it facing that target
/// between spell casts and melee attacks. Sits just below action goals so it is displaced when any
/// combat goal is active but takes over as soon as they release controls.
public class FaceTargetGoal extends Goal {
    private final SummonedEntity entity;

    public FaceTargetGoal(SummonedEntity entity) {
        this.entity = entity;
        setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = entity.getTarget();
        return target != null && target.isAlive() && entity.isActive();
    }

    @Override
    public boolean canContinueToUse() { return canUse(); }

    @Override
    public boolean requiresUpdateEveryTick() { return true; }

    @Override
    public void tick() {
        LivingEntity target = entity.getTarget();
        if (target == null) return;
        entity.getLookControl().setLookAt(target, 30F, 30F);
        entity.setYBodyRot(entity.getYHeadRot());
    }
}
