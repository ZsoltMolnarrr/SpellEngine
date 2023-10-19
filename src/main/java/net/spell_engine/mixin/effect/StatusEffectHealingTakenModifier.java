package net.spell_engine.mixin.effect;

import net.minecraft.entity.effect.StatusEffect;
import net.spell_engine.api.effect.HealingTakenModifier;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(StatusEffect.class)
public class StatusEffectHealingTakenModifier implements HealingTakenModifier {
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
