package net.spell_engine.mixin.item;

import net.minecraft.item.ItemStack;
import net.spell_engine.rpg_series.item.LazyRepair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/// `Item.canRepair` was removed in 1.21.2; anvil repair only consults the `minecraft:repairable` component,
/// which is baked at item construction. Items carrying SpellEngine's transient `spell_engine:lazy_repair`
/// component (weapons via `Weapon.CustomMaterial`, shields via `Shield`) additionally accept their lazily
/// resolved ingredient (cross-mod / config-driven). Non-matching stacks fall through to vanilla unchanged.
@Mixin(ItemStack.class)
public class ItemStackRepairMixin {
    @Inject(method = "canRepairWith", at = @At("HEAD"), cancellable = true)
    private void lazyRepairIngredient_SpellEngine(ItemStack ingredient, CallbackInfoReturnable<Boolean> cir) {
        if (LazyRepair.matches((ItemStack) (Object) this, ingredient)) {
            cir.setReturnValue(true);
        }
    }
}
