package net.spell_engine.mixin.item;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GrindstoneScreenHandler;
import net.spell_engine.api.tags.SpellEngineItemTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GrindstoneScreenHandler.class)
public class GrindstoneScreenHandlerMixin {
    @Inject(method = "getOutputStack", at = @At("HEAD"), cancellable = true)
    private void getOutputStack_SpellEngine(ItemStack firstInput, ItemStack secondInput, CallbackInfoReturnable<ItemStack> cir) {
        if (firstInput.isIn(SpellEngineItemTags.GRINDABLE) && secondInput.isEmpty()) {
            cir.setReturnValue(new ItemStack(Items.PAPER, 1));
            cir.cancel();
        }
    }
}
