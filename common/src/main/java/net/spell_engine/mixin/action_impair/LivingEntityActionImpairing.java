package net.spell_engine.mixin.action_impair;

import net.minecraft.entity.LivingEntity;
import net.spell_engine.api.effect.EntityActionsAllowed;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityActionImpairing {
    @Shadow public abstract void clearActiveItem();

    @Inject(method = "jump", at = @At("HEAD"), cancellable = true)
    private void jump_HEAD_Spell_Engine(CallbackInfo ci) {
        var actionsAllowed = ((EntityActionsAllowed.ControlledEntity) this).actionImpairing();
        if (!actionsAllowed.canJump()) {
            ci.cancel();
        }
    }

    @Inject(method = "tickActiveItemStack", at = @At("HEAD"), cancellable = true)
    private void tickActiveItemStack_HEAD_SpellEngine(CallbackInfo ci) {
        var actionsAllowed = ((EntityActionsAllowed.ControlledEntity) this).actionImpairing();
        if (!actionsAllowed.players().canUseItem()) {
            clearActiveItem();
            ci.cancel();
        }
    }
}
