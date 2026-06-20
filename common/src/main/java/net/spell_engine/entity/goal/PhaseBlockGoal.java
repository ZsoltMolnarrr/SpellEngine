package net.spell_engine.entity.goal;

import net.minecraft.entity.ai.goal.Goal;
import net.spell_engine.entity.SummonedEntity;

import java.util.EnumSet;

/// Holds MOVE/LOOK/JUMP controls during spawn and despawn phases, making the entity inactionable.
public class PhaseBlockGoal extends Goal {
    private final SummonedEntity entity;

    public PhaseBlockGoal(SummonedEntity entity) {
        this.entity = entity;
        setControls(EnumSet.of(Control.MOVE, Control.LOOK, Control.JUMP));
    }

    @Override
    public boolean canStart() { return !entity.isActive(); }

    @Override
    public boolean shouldContinue() { return !entity.isActive(); }
}
