package net.spell_engine.mixin.client.control;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.spell_engine.client.input.InputHelper;
import net.spell_engine.internals.SpellCasterClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerInventory.class)
public class PlayerInventoryMixin {
    @Inject(method = "scrollInHotbar", at = @At("HEAD"), cancellable = true)
    public void scrollInHotbar_OverrideForSpellHotbar(double scrollAmount, CallbackInfo ci) {
        if (InputHelper.shouldControlSpellHotbar()) {
            ci.cancel();
            int delta = (int)Math.signum(scrollAmount) * -1;
            var player = MinecraftClient.getInstance().player;
            if (player != null) {
                ((SpellCasterClient)player).changeSelectedSpellIndex(delta);
            }
        }
    }
}
