package net.spell_engine.mixin.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.spell_engine.api.effect.HealthImpacting;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(LivingEntity.class)
public abstract class LivingEntityStatusHealthImpacting {
    @Shadow @Final private Map<StatusEffect, StatusEffectInstance> activeStatusEffects;
    private float healingTakenModifier_SpellEngine = 1F;
    private float damageTakenModifier_SpellEngine = 1F;
    /**
     * `updatePotionVisibility` is called upon effects of the entity are changed.
     */
    @Inject(method = "updatePotionVisibility", at = @At("HEAD"))
    private void updatePotionVisibility_HEAD_SpellEngine_UpdateHealingTaken(CallbackInfo ci) {
        var healingNewValue = 1F;
        var damageNewValue = 1F;
        for (var entry : activeStatusEffects.entrySet()) {
            var effect = entry.getKey();
            var instance = entry.getValue();
            var stacks = instance.getAmplifier() + 1;
            healingNewValue += ((HealthImpacting)effect).getHealingTakenModifierPerStack() * stacks;
            damageNewValue += ((HealthImpacting)effect).getDamageTakenModifierPerStack() * stacks;
        }
        healingTakenModifier_SpellEngine = healingNewValue;
        damageTakenModifier_SpellEngine = damageNewValue;
    }

    @ModifyVariable(method = "heal", at = @At("HEAD"), argsOnly = true)
    private float modifyHealingTaken_SpellEngine(float amount) {
        return amount * healingTakenModifier_SpellEngine;
    }

    @ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true)
    public float modifyDamageTaken_SpellEngine(float amount) {
        return amount * damageTakenModifier_SpellEngine;
    }
}
