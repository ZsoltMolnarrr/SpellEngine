package net.spell_engine.mixin.item;

import net.minecraft.item.ItemStack;
import net.spell_engine.api.tags.SpellEngineItemTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// GrindstoneScreenHandler$2
//
// Anonymous mixin class to modify GrindstoneScreenHandler `new Slot(...) { ... }` instance
// Using `targets = ...` here due to being an anonymous class
// Class name found by clicking into the class, and using `Top menu > View > Show Bytecode`
@Mixin(targets = "net.minecraft.screen.GrindstoneScreenHandler$2")
public class GrindstoneSlotInputMixin {
    @Inject(method = "canInsert", at = @At("HEAD"), cancellable = true)
    private void canInsert_SpellEngine(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.isIn(SpellEngineItemTags.GRINDABLE)) {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }
}
