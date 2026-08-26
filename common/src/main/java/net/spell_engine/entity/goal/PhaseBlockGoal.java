package net.spell_engine.entity.goal;

import net.minecraft.world.entity.ai.goal.Goal;
import net.spell_engine.entity.SummonedEntity;

import java.util.EnumSet;

/// Holds MOVE/LOOK/JUMP controls during spawn and despawn phases, making the entity inactionable.
public class PhaseBlockGoal extends Goal {
    private final SummonedEntity entity;

    public PhaseBlockGoal(SummonedEntity entity) {
        this.entity = entity;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() { return !entity.isActive(); }

    @Override
    public boolean canContinueToUse() { return !entity.isActive(); }
}
