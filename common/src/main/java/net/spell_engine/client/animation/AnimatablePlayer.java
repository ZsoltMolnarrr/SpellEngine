package net.spell_engine.client.animation;

import net.spell_engine.internals.SpellCast;

public interface AnimatablePlayer {
    void playSpellAnimation(SpellCast.Animation type, String name);
    void updateSpellCastAnimationsOnTick();
}
