package net.spell_engine.mixin.action_impair;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.spell_engine.api.effect.EntityActionsAllowed;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(LivingEntity.class)
public abstract class LivingEntityActionImpairing implements EntityActionsAllowed.ControlledEntity {
    @Shadow public abstract void clearActiveItem();

    @Inject(method = "jump", at = @At("HEAD"), cancellable = true)
    private void jump_HEAD_Spell_Engine(CallbackInfo ci) {
        if (EntityActionsAllowed.isImpaired((LivingEntity) ((Object) this),
                EntityActionsAllowed.Common.JUMP)) {
            ci.cancel();
        }
    }

    @Inject(method = "tickActiveItemStack", at = @At("HEAD"), cancellable = true)
    private void tickActiveItemStack_HEAD_SpellEngine(CallbackInfo ci) {
        if (EntityActionsAllowed.isImpaired((LivingEntity) ((Object) this),
                EntityActionsAllowed.Player.ITEM_USE)) {
            clearActiveItem();
            ci.cancel();
        }
    }

    @Inject(method = "isImmobile", at = @At("HEAD"), cancellable = true)
    private void isImmobile_HEAD_SpellEngine(CallbackInfoReturnable<Boolean> cir) {
        if (EntityActionsAllowed.isImpaired((LivingEntity) ((Object) this),
                EntityActionsAllowed.Common.MOVE)) {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }


    // MARK: Actions Allowed (CC)

    private EntityActionsAllowed entityActionsAllowed_SpellEngine = EntityActionsAllowed.ANY;

    @Shadow private boolean effectsChanged;
    @Shadow @Final
    private Map<StatusEffect, StatusEffectInstance> activeStatusEffects;

//    @Inject(method = "tickStatusEffects", at = @At("HEAD")) // This way, we don't get notified about removals on server side
    @Inject(method = "tickStatusEffects", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/LivingEntity;effectsChanged:Z", shift = At.Shift.BEFORE, ordinal = 0))
    private void tickStatusEffects_HEAD_SpellEngine(CallbackInfo ci) {
        if (effectsChanged) {
            updateEntityActionsAllowed();
        }
    }

    public void updateEntityActionsAllowed() {
        entityActionsAllowed_SpellEngine = EntityActionsAllowed.fromEffects(activeStatusEffects.keySet());
    }

    public EntityActionsAllowed actionImpairing() {
        return entityActionsAllowed_SpellEngine;
    }
}
