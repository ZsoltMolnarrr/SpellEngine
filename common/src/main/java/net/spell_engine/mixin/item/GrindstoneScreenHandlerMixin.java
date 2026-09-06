package net.spell_engine.mixin.item;

import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GrindstoneScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.spell_engine.api.tags.SpellEngineItemTags;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/// Grinding a `#spell_engine:grindable` item (spell books / scrolls) yields paper.
/// 1.20.1: `GrindstoneScreenHandler` has no `getOutputStack(ItemStack, ItemStack)` (1.21 extracted it) — the
/// result is computed inline in `updateResult()`, so the override is applied at its HEAD.
@Mixin(GrindstoneScreenHandler.class)
public abstract class GrindstoneScreenHandlerMixin {
    @Shadow @Final private Inventory result;
    @Shadow @Final Inventory input;

    @Inject(method = "updateResult", at = @At("HEAD"), cancellable = true)
    private void updateResult_HEAD_SpellEngine(CallbackInfo ci) {
        var firstInput = input.getStack(0);
        var secondInput = input.getStack(1);
        if (firstInput.isIn(SpellEngineItemTags.GRINDABLE) && secondInput.isEmpty()) {
            result.setStack(0, new ItemStack(Items.PAPER, 1));
            ((ScreenHandler) (Object) this).sendContentUpdates();
            ci.cancel();
        }
    }
}
