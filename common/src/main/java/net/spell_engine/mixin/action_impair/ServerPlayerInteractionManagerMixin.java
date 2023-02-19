package net.spell_engine.mixin.action_impair;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import net.spell_engine.api.effect.EntityActionsAllowed;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerInteractionManager.class)
public class ServerPlayerInteractionManagerMixin {
    @Inject(method = "interactItem", at = @At("HEAD"), cancellable = true)
    private void interactItem_HEAD_SpellEngine(ServerPlayerEntity player, World world, ItemStack stack, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        var actionsAllowed = ((EntityActionsAllowed.ControlledEntity) player).actionImpairing();
        if (!actionsAllowed.players().canUseItem()) {
            cir.setReturnValue(ActionResult.FAIL);
            cir.cancel();
        }
    }
}
