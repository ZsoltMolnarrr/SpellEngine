package net.spell_engine.mixin.item;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtElement;
import net.minecraft.text.Text;
import net.minecraft.util.Language;
import net.minecraft.util.Rarity;
import net.spell_engine.api.item.SpellItemData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/// Applies the per-stack name and rarity overrides of the NBT facade (`spell_engine.item_name` /
/// `spell_engine.rarity`, the 1.20.1 stand-ins for the 1.21 `item_name` / `rarity` data components).
///
/// This lives on `ItemStack` rather than on `Item#getName(ItemStack)` deliberately: the spell book and
/// scroll items are swapped for slot-mod subclasses when Trinkets (Fabric) or Curios (Forge) is present
/// (`SlotModCompat.spellBookFactory`), and those extend `TrinketItem` / `Item` directly, so any override
/// living on `UniversalSpellBookItem` / `ScrollItem` is simply not on the registered item. Components were
/// item-class independent on 1.21; so is this.
///
/// Precedence mirrors 1.21: an explicit custom name (`display.Name`, i.e. anvil-renamed) beats the
/// item name override, which beats the item's own translation key.
@Mixin(ItemStack.class)
public class ItemStackNameMixin {
    @Inject(method = "getName", at = @At("HEAD"), cancellable = true)
    private void spellEngine_itemName(CallbackInfoReturnable<Text> cir) {
        var stack = (ItemStack) (Object) this;
        var display = stack.getSubNbt("display");
        if (display != null && display.contains("Name", NbtElement.STRING_TYPE)) {
            return; // Anvil/custom name wins, as on 1.21
        }
        var key = SpellItemData.getItemNameKey(stack);
        // The key is written wherever the stack is configured — including on the logical server, where
        // client-side resource-pack translations are not visible — so the "is it translatable" check
        // belongs here, at display time, not at write time.
        if (key != null && !key.isEmpty() && Language.getInstance().hasTranslation(key)) {
            cir.setReturnValue(Text.translatable(key));
        }
    }

    @Inject(method = "getRarity", at = @At("HEAD"), cancellable = true)
    private void spellEngine_rarity(CallbackInfoReturnable<Rarity> cir) {
        var rarity = SpellItemData.getRarity((ItemStack) (Object) this);
        if (rarity != null) {
            cir.setReturnValue(rarity);
        }
    }
}
