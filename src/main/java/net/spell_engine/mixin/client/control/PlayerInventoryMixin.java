package net.spell_engine.mixin.client.control;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.spell_engine.client.input.InputHelper;
import net.spell_engine.internals.SpellCasterClient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerInventory.class)
public class PlayerInventoryMixin {
    @Shadow @Final public PlayerEntity player;

    @Inject(method = "scrollInHotbar", at = @At("HEAD"), cancellable = true)
    public void scrollInHotbar_OverrideForSpellHotbar(double scrollAmount, CallbackInfo ci) {
        if (InputHelper.shouldControlSpellHotbar()) {
            ci.cancel();
            int delta = (int)Math.signum(scrollAmount) * -1;
            var caster = player;
            if (caster != null) {
                ((SpellCasterClient)caster).changeSelectedSpellIndex(delta);
            }
        }
    }
}
