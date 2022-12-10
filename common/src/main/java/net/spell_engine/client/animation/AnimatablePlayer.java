package net.spell_engine.client.animation;

import net.spell_engine.internals.SpellAnimationType;

public interface AnimatablePlayer {
    void playAnimation(SpellAnimationType type, String name);
    void updateCastAnimationsOnTick();
}
