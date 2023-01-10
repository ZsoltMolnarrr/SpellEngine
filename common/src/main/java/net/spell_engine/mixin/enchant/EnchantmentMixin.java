package net.spell_engine.mixin.enchant;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.spell_engine.api.enchantment.EnchantmentRestriction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Enchantment.class)
public class EnchantmentMixin {
    @Inject(method = "isAcceptableItem", at = @At("HEAD"), cancellable = true)
    public void isAcceptableItem_HEAD_SpellEngine(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        var enchantment = (Enchantment)((Object)this);
        if (EnchantmentRestriction.isAlleviated(enchantment, stack)) {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }
}
