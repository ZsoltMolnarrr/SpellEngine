package net.spell_engine.mixin.effect;

import net.minecraft.entity.effect.StatusEffect;
import net.spell_engine.api.effect.HealthImpacting;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(StatusEffect.class)
public class StatusEffectHealthImpacting implements HealthImpacting {
    private float damageTakenModifierPerStack_SpellEngine = 0;
    @Override
    public float getDamageTakenModifierPerStack() {
        return damageTakenModifierPerStack_SpellEngine;
    }
    @Override
    public void setDamageTakenModifierPerStack(float modifierPerStack) {
        damageTakenModifierPerStack_SpellEngine = modifierPerStack;
    }

    private float healingTakenModifierPerStack_SpellEngine = 0;
    @Override
    public float getHealingTakenModifierPerStack() {
        return healingTakenModifierPerStack_SpellEngine;
    }
    @Override
    public void setHealingTakenModifierPerStack(float modifierPerStack) {
        healingTakenModifierPerStack_SpellEngine = modifierPerStack;
    }
}
