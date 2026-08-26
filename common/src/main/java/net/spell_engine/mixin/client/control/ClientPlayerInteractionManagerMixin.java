package net.spell_engine.mixin.client.control;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.effect.EntityActionsAllowed;
import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.client.input.MinecraftClientExtension;
import net.spell_engine.client.input.SpellHotbar;
import net.spell_engine.spellbinding.spellchoice.SpellChoices;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(MultiPlayerGameMode.class)
public class ClientPlayerInteractionManagerMixin {
    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
    public void interactItem_HEAD_LockHotbar(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (player instanceof LocalPlayer clientPlayer) {
            ItemStack stack = player.getItemInHand(hand);
            if (SpellChoices.from(stack) != null) {
                return;
            }
            
            if (SpellHotbar.INSTANCE.lastHandled() == null) {
                var handled = SpellHotbar.INSTANCE.handleUseKey(clientPlayer, minecraft.options);
                ((MinecraftClientExtension) minecraft).onSpellHotbarInputHandled(handled);
            }
            if (((MinecraftClientExtension)minecraft).isSpellCastLockActive()) {
                cir.setReturnValue(InteractionResult.FAIL);
                cir.cancel();
            }
        }
        if (EntityActionsAllowed.isImpaired(player, EntityActionsAllowed.Player.ITEM_USE, true)) {
            cir.setReturnValue(InteractionResult.FAIL);
            cir.cancel();
        }
    }
}
