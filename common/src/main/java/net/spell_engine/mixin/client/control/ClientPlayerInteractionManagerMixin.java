package net.spell_engine.mixin.client.control;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.spell_engine.api.effect.EntityActionsAllowed;
import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.client.input.InputHelper;
import net.spell_engine.internals.SpellCasterItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {
    @Inject(method = "interactItem", at = @At("HEAD"), cancellable = true)
    public void interactItem_HEAD_LockHotbar(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        var actionsAllowed = ((EntityActionsAllowed.ControlledEntity) player).actionImpairing();
        if (!actionsAllowed.players().canUseItem()) {
            cir.setReturnValue(ActionResult.FAIL);
            cir.cancel();
            return;
        }
        if(SpellEngineClient.config.lockHotbarOnRightClick && !InputHelper.isLocked) {
            ItemStack itemStack = player.getStackInHand(hand);
            var object = (Object) itemStack;
            if (object instanceof SpellCasterItemStack stack) {
                if (InputHelper.canLockOnContainer(stack.getSpellContainer())) {
                    InputHelper.isLocked = true;
                    InputHelper.showLockedMessage("ESC");
                    cir.setReturnValue(ActionResult.PASS);
                    cir.cancel();
                }
            }
        }
    }
}
