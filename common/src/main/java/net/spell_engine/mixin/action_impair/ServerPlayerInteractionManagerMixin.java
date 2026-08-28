package net.spell_engine.mixin.action_impair;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.spell_engine.api.effect.EntityActionsAllowed;
import net.spell_engine.spellbinding.spellchoice.SpellChoiceScreenHandler;
import net.spell_engine.spellbinding.spellchoice.SpellChoices;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
public class ServerPlayerInteractionManagerMixin {
    @Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
    private void interactItem_HEAD_SpellEngine(ServerPlayer player, Level world, ItemStack stack, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (EntityActionsAllowed.isImpaired(player, EntityActionsAllowed.Player.ITEM_USE)) {
            cir.setReturnValue(InteractionResult.FAIL);
            cir.cancel();
            return;
        }
        if (SpellChoices.from(stack) != null) {
            player.openMenu(new SimpleMenuProvider(
                    (syncId, inventory, playerEntity) ->
                            new SpellChoiceScreenHandler(syncId, stack, inventory, ContainerLevelAccess.create(world, player.blockPosition())),
                    Component.translatable("spell.tooltip.choice.list.spell")
            ));
            cir.setReturnValue(InteractionResult.SUCCESS);
            cir.cancel();
        }
    }
}
