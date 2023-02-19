package net.spell_engine.mixin.action_impair;

import net.minecraft.entity.mob.MobEntity;
import net.spell_engine.api.effect.EntityActionsAllowed;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MobEntity.class)
public class MobEntityActionImpairing {
    @Inject(method = "isAiDisabled", at = @At("HEAD"), cancellable = true)
    private void isAiDisabled_HEAD_SpellEngine(CallbackInfoReturnable<Boolean> cir) {
        var actionsAllowed = ((EntityActionsAllowed.ControlledEntity) this).actionImpairing();
        if (!actionsAllowed.mobs().canUseAI()) {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }

    @Inject(method = "canMoveVoluntarily", at = @At("HEAD"), cancellable = true)
    private void canMoveVoluntarily_HEAD_SpellEngine(CallbackInfoReturnable<Boolean> cir) {
        var actionsAllowed = ((EntityActionsAllowed.ControlledEntity) this).actionImpairing();
        if (!actionsAllowed.canMove()) {
            cir.setReturnValue(false);
            cir.cancel();
        }
    }
}