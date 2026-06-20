package net.spell_engine.entity.goal;

import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.spell_engine.entity.SummonedEntity;

/// Vanilla wander, gated on target presence: the summon stops drifting the moment a target is
/// acquired by any route, and won't restart wandering until the target is gone.
public class WanderWhenIdleGoal extends WanderAroundFarGoal {
    private final SummonedEntity entity;

    public WanderWhenIdleGoal(SummonedEntity entity, double speed, float probability) {
        super(entity, speed, probability);
        this.entity = entity;
    }

    @Override
    public boolean canStart() {
        if (entity.hasLiveTarget()) return false;
        return super.canStart();
    }

    @Override
    public boolean shouldContinue() {
        if (entity.hasLiveTarget()) return false;
        return super.shouldContinue();
    }
}
