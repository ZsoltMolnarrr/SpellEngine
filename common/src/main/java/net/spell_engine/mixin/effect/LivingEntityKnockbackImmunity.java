package net.spell_engine.mixin.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.entry.RegistryEntry;
import net.spell_engine.api.effect.KnockbackImmunity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(LivingEntity.class)
public abstract class LivingEntityKnockbackImmunity implements KnockbackImmunity.Bearer {
    // Non-persisted cache: `takeKnockback` is a hot path, so we read this flag instead of
    // scanning active effects. Refreshed only when the effect set changes (see below).
    private boolean knockbackImmune_SpellEngine = false;

    @Shadow private boolean effectsChanged;
    @Shadow @Final private Map<RegistryEntry<StatusEffect>, StatusEffectInstance> activeStatusEffects;

    @Inject(method = "tickStatusEffects", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/LivingEntity;effectsChanged:Z", shift = At.Shift.BEFORE, ordinal = 0))
    private void tickStatusEffects_HEAD_KnockbackImmunity_SpellEngine(CallbackInfo ci) {
        if (effectsChanged) {
            knockbackImmune_SpellEngine = KnockbackImmunity.anyImmune(activeStatusEffects.keySet());
        }
    }

    @Override
    public boolean SpellEngine_isImmuneToKnockback() {
        return knockbackImmune_SpellEngine;
    }

    @Inject(method = "takeKnockback", at = @At("HEAD"), cancellable = true)
    private void takeKnockback_HEAD_KnockbackImmunity_SpellEngine(double strength, double x, double z, CallbackInfo ci) {
        if (knockbackImmune_SpellEngine) {
            ci.cancel();
        }
    }
}
