package net.spell_engine.mixin.client.action_impair;

import net.spell_engine.mixin.client.control.InputAccessor;
import net.minecraft.util.math.Vec2f;
import net.minecraft.client.network.ClientPlayerEntity;
import net.spell_engine.api.effect.EntityActionsAllowed;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public class ClientPlayerActionImpairing {
    @Inject(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/input/Input;tick()V", shift = At.Shift.AFTER))
    private void tickMovement_ModifyInput_SpellEngine_ActionImpairing(CallbackInfo ci) {
        var clientPlayer = (ClientPlayerEntity)((Object)this);
        if (EntityActionsAllowed.isImpaired(clientPlayer, EntityActionsAllowed.Common.MOVE)) {
            ((InputAccessor) clientPlayer.input).spellEngine_setMovementVector(Vec2f.ZERO);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void tick_TAIL_SpellEngine_ActionImpairing(CallbackInfo ci) {
        ((EntityActionsAllowed.ControlledEntity)this).updateEntityActionsAllowed();
    }
}
