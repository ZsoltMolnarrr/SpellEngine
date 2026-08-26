package net.spell_engine.mixin.client.action_impair;

import net.spell_engine.mixin.client.control.InputAccessor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec2;
import net.spell_engine.api.effect.EntityActionsAllowed;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public class ClientPlayerActionImpairing {
    @Inject(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/ClientInput;tick()V", shift = At.Shift.AFTER))
    private void tickMovement_ModifyInput_SpellEngine_ActionImpairing(CallbackInfo ci) {
        var clientPlayer = (LocalPlayer)((Object)this);
        if (EntityActionsAllowed.isImpaired(clientPlayer, EntityActionsAllowed.Common.MOVE)) {
            ((InputAccessor) clientPlayer.input).spellEngine_setMovementVector(Vec2.ZERO);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void tick_TAIL_SpellEngine_ActionImpairing(CallbackInfo ci) {
        ((EntityActionsAllowed.ControlledEntity)this).updateEntityActionsAllowed();
    }
}
