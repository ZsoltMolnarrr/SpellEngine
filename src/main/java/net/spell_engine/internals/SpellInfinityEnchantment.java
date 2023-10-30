package net.spell_engine.internals;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentTarget;
import net.minecraft.enchantment.MendingEnchantment;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.spell.SpellContainer;
import net.tinyconfig.models.EnchantmentConfig;

public class SpellInfinityEnchantment extends Enchantment {
    public EnchantmentConfig config;

    public SpellInfinityEnchantment(Enchantment.Rarity weight, EnchantmentConfig config, EquipmentSlot... slotTypes) {
        super(weight, EnchantmentTarget.BREAKABLE, slotTypes);
        this.config = config;
    }

    public static boolean itemStackHasSpell(ItemStack stack) {
        var object = (Object)stack;
        if (object instanceof SpellCasterItemStack casterItemStack) {
            var container = casterItemStack.getSpellContainer();
            return container != null && container.isValid() && container.content == SpellContainer.ContentType.MAGIC;
        }
        return false;
    }

    // MARK: Cost

    public int getMaxLevel() {
        if (!SpellEngineMod.config.spell_cost_item_allowed) {
            return 0;
        }
        return config.max_level;
    }

    public int getMinPower(int level) {
        return config.min_cost + (level - 1) * config.step_cost;
    }

    public int getMaxPower(int level) {
        return super.getMinPower(level) + 50;
    }

    // MARK: Accepting others

    public boolean canAccept(Enchantment other) {
        return (other instanceof MendingEnchantment) ? false : super.canAccept(other);
    }
}
