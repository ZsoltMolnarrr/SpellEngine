package net.spell_engine.mixin.criteria;

import net.minecraft.advancement.criterion.EnchantedItemCriterion;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.registry.Registry;
import net.spell_engine.internals.criteria.EnchantmentSpecificCriteria;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnchantedItemCriterion.class)
public class EnchantedItemCriterionMixin {
    @Inject(method = "trigger", at = @At("HEAD"))
    private void trigger_HEAD_SpellEngine(ServerPlayerEntity player, ItemStack stack, int levels, CallbackInfo ci) {
        var enchants = EnchantmentHelper.get(stack);
        for(var entry: enchants.entrySet()) {
            var enchantment = entry.getKey();
            var id = Registry.ENCHANTMENT.getId(enchantment);
            if (id != null) {
                EnchantmentSpecificCriteria.INSTANCE.trigger(player, id.toString());
            }
        }
    }
}
