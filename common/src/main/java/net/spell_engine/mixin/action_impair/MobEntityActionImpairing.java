package net.spell_engine.mixin.action_impair;

import net.minecraft.world.entity.Mob;
import net.spell_engine.api.effect.EntityActionsAllowed;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public class MobEntityActionImpairing {
    @Inject(method = "isNoAi", at = @At("HEAD"), cancellable = true)
    private void isAiDisabled_HEAD_SpellEngine(CallbackInfoReturnable<Boolean> cir) {
        if (EntityActionsAllowed.isImpaired((Mob) ((Object) this),
                EntityActionsAllowed.Mob.USE_AI)) {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }
}