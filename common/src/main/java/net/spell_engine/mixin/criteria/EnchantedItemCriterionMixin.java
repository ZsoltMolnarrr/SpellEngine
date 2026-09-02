package net.spell_engine.mixin.criteria;

import net.minecraft.advancements.triggers.EnchantedItemTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.spell_engine.misc.criteria.EnchantmentSpecificCriteria;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnchantedItemTrigger.class)
public class EnchantedItemCriterionMixin {
    @Inject(method = "trigger", at = @At("HEAD"))
    private void trigger_HEAD_SpellEngine(ServerPlayer player, ItemStack stack, int levels, CallbackInfo ci) {
        var enchants = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        for(var entry: enchants.keySet()) {
            var id = entry.unwrapKey().get().identifier();
            if (id != null) {
                EnchantmentSpecificCriteria.INSTANCE.trigger(player, id);
            }
        }
    }
}
