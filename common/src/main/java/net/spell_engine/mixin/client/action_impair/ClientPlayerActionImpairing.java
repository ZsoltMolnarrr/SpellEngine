package net.spell_engine.mixin.client.action_impair;

import net.minecraft.client.network.ClientPlayerEntity;
import net.spell_engine.api.effect.EntityActionsAllowed;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerEntity.class)
public class ClientPlayerActionImpairing {
    @Inject(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/input/Input;tick(ZF)V", shift = At.Shift.AFTER))
    private void tickMovement_ModifyInput_SpellEngine_ActionImpairing(CallbackInfo ci) {
        var clientPlayer = (ClientPlayerEntity)((Object)this);
        if (EntityActionsAllowed.isImpaired(clientPlayer, EntityActionsAllowed.Common.MOVE)) {
            clientPlayer.input.movementForward = 0;
            clientPlayer.input.movementSideways = 0;
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void tick_TAIL_SpellEngine_ActionImpairing(CallbackInfo ci) {
        ((EntityActionsAllowed.ControlledEntity)this).updateEntityActionsAllowed();
    }

    @Inject(method = "canMoveVoluntarily", at = @At("HEAD"), cancellable = true)
    private void canMoveVoluntarily_HEAD_SpellEngine(CallbackInfoReturnable<Boolean> cir) {
        var clientPlayer = (ClientPlayerEntity)((Object)this);
        if (EntityActionsAllowed.isImpaired(clientPlayer, EntityActionsAllowed.Common.MOVE)) {
            cir.setReturnValue(false);
            cir.cancel();
        }
    }
}
