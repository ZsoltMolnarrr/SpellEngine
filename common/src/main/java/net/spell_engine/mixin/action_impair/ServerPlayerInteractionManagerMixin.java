package net.spell_engine.mixin.action_impair;

import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import net.spell_engine.api.effect.EntityActionsAllowed;
import net.spell_engine.spellbinding.spellchoice.SpellChoiceScreenHandler;
import net.spell_engine.spellbinding.spellchoice.SpellChoices;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerInteractionManager.class)
public class ServerPlayerInteractionManagerMixin {
    @Inject(method = "interactItem", at = @At("HEAD"), cancellable = true)
    private void interactItem_HEAD_SpellEngine(ServerPlayerEntity player, World world, ItemStack stack, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if (EntityActionsAllowed.isImpaired(player, EntityActionsAllowed.Player.ITEM_USE)) {
            cir.setReturnValue(ActionResult.FAIL);
            cir.cancel();
        }
        if (SpellChoices.from(stack) != null) {
            player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                    (syncId, inventory, playerEntity) ->
                            new SpellChoiceScreenHandler(syncId, stack, inventory, ScreenHandlerContext.create(world, player.getBlockPos())),
                    Text.translatable("spell.tooltip.choice.list.spell")
            ));
            cir.setReturnValue(ActionResult.SUCCESS);
            cir.cancel();
        }
    }
}
