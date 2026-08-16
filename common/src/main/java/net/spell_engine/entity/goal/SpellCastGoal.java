package net.spell_engine.entity.goal;

import net.minecraft.entity.ai.goal.Goal;
import net.spell_engine.api.spell.summon.SummonBehaviour;
import net.spell_engine.entity.SummonedEntity;
import net.spell_engine.internals.casting.MobCastController;

import java.util.EnumSet;

/// Thin vanilla-`Goal` adapter over {@link MobCastController}, which owns the whole cast
/// lifecycle (aiming, engagement-gated timing, fire, cooldown). This class carries only what
/// the `Goal` contract itself demands.
public class SpellCastGoal extends Goal {
    private final MobCastController controller;

    public SpellCastGoal(SummonedEntity entity, SummonBehaviour.Action.SpellCast config) {
        this.controller = new MobCastController(entity, config);
        setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean shouldRunEveryTick() { return true; }

    @Override
    public boolean canStart() { return controller.canBegin(); }

    @Override
    public void start() { controller.begin(); }

    @Override
    public boolean shouldContinue() { return controller.shouldContinue(); }

    @Override
    public void stop() { controller.end(); }

    @Override
    public void tick() { controller.tick(); }
}
