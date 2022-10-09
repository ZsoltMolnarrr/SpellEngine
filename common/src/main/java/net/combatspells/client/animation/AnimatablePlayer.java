package net.combatspells.client.animation;

import net.combatspells.internals.SpellAnimationType;

public interface AnimatablePlayer {
    void playAnimation(SpellAnimationType type, String name);
    void updateCastAnimationsOnTick();
}
