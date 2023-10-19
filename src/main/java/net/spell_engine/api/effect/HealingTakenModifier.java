package net.spell_engine.api.effect;

import net.minecraft.entity.effect.StatusEffect;

public interface HealingTakenModifier {
    float getHealingTakenModifierPerStack();
    void setHealingTakenModifierPerStack(float modifierPerStack);

    /**
     * Set the healing taken modifier per stack for the given effect.
     * @param effect the effect instance to configure
     * @param modifierPerStack
     *      - use 0.1F for +10% healing taken per stack
     *      -use -0.1F for -10% healing taken per stack
     */
    static void configure(StatusEffect effect, float modifierPerStack) {
        ((HealingTakenModifier)effect).setHealingTakenModifierPerStack(modifierPerStack);
    }
}
