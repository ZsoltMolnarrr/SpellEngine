package net.spell_engine.api.effect;

import net.minecraft.entity.effect.StatusEffect;

public interface HealthImpacting {

    float getDamageTakenModifierPerStack();
    void setDamageTakenModifierPerStack(float modifierPerStack);
    /**
     * Set the damage taken modifier per stack for the given effect.
     * @param effect the effect instance to configure
     * @param modifierPerStack
     *      - use 0.1F for +10% healing taken per stack
     *      -use -0.1F for -10% healing taken per stack
     */
    static void configureDamageTaken(StatusEffect effect, float modifierPerStack) {
        ((HealthImpacting)effect).setHealingTakenModifierPerStack(modifierPerStack);
    }


    float getHealingTakenModifierPerStack();
    void setHealingTakenModifierPerStack(float modifierPerStack);

    /**
     * Set the healing taken modifier per stack for the given effect.
     * @param effect the effect instance to configure
     * @param modifierPerStack
     *      - use 0.1F for +10% healing taken per stack
     *      -use -0.1F for -10% healing taken per stack
     */
    static void configureHealingTaken(StatusEffect effect, float modifierPerStack) {
        ((HealthImpacting)effect).setHealingTakenModifierPerStack(modifierPerStack);
    }
}
