package net.spell_engine.mixin.effect;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
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

    @Shadow private boolean effectsDirty;
    @Shadow @Final private Map<Holder<MobEffect>, MobEffectInstance> activeEffects;

    // 1.21.11: the `effectsChanged` check moved out of `tickStatusEffects` into `handleEffectsChanged` (called right after it)
    @Inject(method = "updateDirtyEffects", at = @At("HEAD"))
    private void handleEffectsChanged_HEAD_KnockbackImmunity_SpellEngine(CallbackInfo ci) {
        if (effectsDirty) {
            knockbackImmune_SpellEngine = KnockbackImmunity.anyImmune(activeEffects.keySet());
        }
    }

    @Override
    public boolean SpellEngine_isImmuneToKnockback() {
        return knockbackImmune_SpellEngine;
    }

    @Inject(method = "knockback", at = @At("HEAD"), cancellable = true)
    private void takeKnockback_HEAD_KnockbackImmunity_SpellEngine(double strength, double x, double z, CallbackInfo ci) {
        if (knockbackImmune_SpellEngine) {
            ci.cancel();
        }
    }
}
