package net.spell_engine.mixin.client.control;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
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
        if(SpellEngineClient.config.lockHotbarOnRightClick && !InputHelper.isLocked) {
            ItemStack itemStack = player.getStackInHand(hand);
            var object = (Object) itemStack;
            if (object instanceof SpellCasterItemStack stack) {
                var container = stack.getSpellContainer();
                if (container != null && container.isUsable()) {
                    InputHelper.isLocked = true;
                    InputHelper.showLockedMessage("ESC");
                    cir.setReturnValue(ActionResult.PASS);
                    cir.cancel();
                }
            }
        }
    }
}
