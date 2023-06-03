package net.spell_engine.mixin.action_impair;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.spell_engine.api.effect.EntityActionsAllowed;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractHorseEntity.class)
public class AbstractHorseEntityMixin {
    @Inject(method = "isImmobile", at = @At("HEAD"), cancellable = true)
    private void isImmobile_HEAD_Horse_SpellEngine(CallbackInfoReturnable<Boolean> cir) {
        if (EntityActionsAllowed.isImpaired((LivingEntity) ((Object) this),
                EntityActionsAllowed.Common.MOVE)) {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }
}
