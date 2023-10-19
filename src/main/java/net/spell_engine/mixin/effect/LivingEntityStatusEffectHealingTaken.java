package net.spell_engine.mixin.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.spell_engine.api.effect.HealingTakenModifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(LivingEntity.class)
public abstract class LivingEntityStatusEffectHealingTaken {
    @Shadow @Final private Map<StatusEffect, StatusEffectInstance> activeStatusEffects;
    private float healingTakenModifier_SpellEngine = 1F;
    /**
     * `updatePotionVisibility` is called upon effects of the entity are changed.
     */
    @Inject(method = "updatePotionVisibility", at = @At("HEAD"))
    private void updatePotionVisibility_HEAD_SpellEngine_UpdateHealingTaken(CallbackInfo ci) {
        var newValue = 1F;
        for (var entry : activeStatusEffects.entrySet()) {
            var effect = entry.getKey();
            var instance = entry.getValue();
            newValue += ((HealingTakenModifier)effect).getHealingTakenModifierPerStack() * (instance.getAmplifier() + 1);
        }
        healingTakenModifier_SpellEngine = newValue;
    }

    @ModifyVariable(method = "heal(F)V", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private float injected(float amount) {
        return amount * healingTakenModifier_SpellEngine;
    }
}
