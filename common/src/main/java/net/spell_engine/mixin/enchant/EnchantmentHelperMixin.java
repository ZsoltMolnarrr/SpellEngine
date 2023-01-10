package net.spell_engine.mixin.enchant;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.registry.Registry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(EnchantmentHelper.class)
public class EnchantmentHelperMixin {
    // Add extra entries those where rejected by Vanilla logic
    @Inject(method = "getPossibleEntries", at = @At("RETURN"))
    private static void getPossibleEntries_RETURN_SpellEngine(int power, ItemStack stack, boolean treasureAllowed, CallbackInfoReturnable<List<EnchantmentLevelEntry>> cir) {
        var currentEntries = cir.getReturnValue();

        // This logic is mostly copied from EnchantmentHelper.getPossibleEntries
        Item item = stack.getItem();
        boolean isBook = stack.isOf(Items.BOOK);
        block0: for (Enchantment enchantment : Registry.ENCHANTMENT) {
            // Don't check things already added
            boolean alreadyAdded = currentEntries.stream().anyMatch(entry -> entry.enchantment.equals(enchantment));
            if (alreadyAdded) { continue; }

            if (enchantment.isTreasure()
                    && !treasureAllowed
                    || !enchantment.isAvailableForRandomSelection()
                    || !enchantment.isAcceptableItem(stack) // Custom logic, replacing `!enchantment.type.isAcceptableItem(item)`
                    && !isBook) continue;
            for (int i = enchantment.getMaxLevel(); i > enchantment.getMinLevel() - 1; --i) {
                if (power < enchantment.getMinPower(i) || power > enchantment.getMaxPower(i)) continue;
                currentEntries.add(new EnchantmentLevelEntry(enchantment, i));
                continue block0;
            }
        }
    }
}
