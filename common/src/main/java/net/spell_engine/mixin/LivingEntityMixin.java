package net.spell_engine.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.spell_engine.entity.ConfigurableKnockback;
import net.spell_engine.internals.SpellCasterClient;
import net.spell_engine.api.effect.EntityActionsAllowed;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin implements ConfigurableKnockback, EntityActionsAllowed.ControlledEntity {

    // MARK: ConfigurableKnockback

    private float customKnockbackMultiplier_SpellEngine = 1;

    @Override
    public void setKnockbackMultiplier_SpellEngine(float value) {
        customKnockbackMultiplier_SpellEngine = value;
    }

    @ModifyVariable(method = "takeKnockback", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    public double takeKnockback_HEAD_changeStrength(double knockbackStrength) {
        return knockbackStrength * customKnockbackMultiplier_SpellEngine;
    }

    // MARK: Spell casting controls

    @Shadow public abstract int getItemUseTimeLeft();

    @Inject(method = "clearActiveItem", at = @At("HEAD"))
    private void clearActiveItem_HEAD_SpellEngine(CallbackInfo ci) {
        var entity = (LivingEntity) ((Object) this);
        if (entity.world.isClient && entity instanceof SpellCasterClient caster) {
            if (caster.getCurrentSpellId() != null) {
                caster.castRelease(entity.getActiveItem(), getItemUseTimeLeft());
            }
        }
    }

    // MARK: Actions Allowed (CC)

    private EntityActionsAllowed entityActionsAllowed_SpellEngine = EntityActionsAllowed.any;

    @Shadow private boolean effectsChanged;
    @Shadow @Final private Map<StatusEffect, StatusEffectInstance> activeStatusEffects;

    @Inject(method = "tickStatusEffects", at = @At("HEAD"))
    private void tickStatusEffects_HEAD_SpellEngine(CallbackInfo ci) {
        if (effectsChanged) {
            entityActionsAllowed_SpellEngine = EntityActionsAllowed.fromEffects(activeStatusEffects.keySet());
        }
    }

    public EntityActionsAllowed actionImpairing() {
        return entityActionsAllowed_SpellEngine;
    }
}
