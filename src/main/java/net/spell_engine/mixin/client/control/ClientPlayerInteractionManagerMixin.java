package net.spell_engine.mixin.client.control;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.spell_engine.api.effect.EntityActionsAllowed;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {
    @Inject(method = "interactItem", at = @At("HEAD"), cancellable = true)
    public void interactItem_HEAD_LockHotbar(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if (EntityActionsAllowed.isImpaired(player, EntityActionsAllowed.Player.ITEM_USE, true)) {
            cir.setReturnValue(ActionResult.FAIL);
            cir.cancel();
        }

        // Maybe handle useKey here?

//        if(SpellEngineClient.config.lockHotbarOnRightClick && !InputHelper.isLocked) {
//            if (InputHelper.hasLockableSpellContainer(player)) {
//                InputHelper.isLocked = true;
//                InputHelper.showLockedMessage("ESC");
//                cir.setReturnValue(ActionResult.PASS);
//                cir.cancel();
//            }
//        }
    }
}
