package net.spell_engine.mixin.client.control;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.spell_engine.api.effect.EntityActionsAllowed;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.client.input.SpellHotbar;
import net.spell_engine.internals.casting.SpellCasterClient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {
    @Shadow @Final private MinecraftClient client;

    @Inject(method = "interactItem", at = @At("HEAD"), cancellable = true)
    public void interactItem_HEAD_LockHotbar(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if (player instanceof ClientPlayerEntity clientPlayer) {
            if (!SpellEngineClient.config.useKeyHighPriority) {
                var handled = SpellHotbar.INSTANCE.handle(clientPlayer, SpellHotbar.INSTANCE.structuredSlots.onUseKey(), client.options);
                if (handled != null
                        && handled.spell().spell().mode != Spell.Mode.BYPASS_TO_ITEM_USE) {
                    cir.setReturnValue(ActionResult.FAIL);
                    cir.cancel();
                }
            }

            var caster = (SpellCasterClient)clientPlayer;
            if (caster.isCastingSpell()) {
                cir.setReturnValue(ActionResult.FAIL);
                cir.cancel();
            }
        }

        if (EntityActionsAllowed.isImpaired(player, EntityActionsAllowed.Player.ITEM_USE, true)) {
            cir.setReturnValue(ActionResult.FAIL);
            cir.cancel();
        }
    }
}
