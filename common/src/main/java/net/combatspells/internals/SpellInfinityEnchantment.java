package net.combatspells.internals;

import net.combatspells.CombatSpells;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentTarget;
import net.minecraft.enchantment.MendingEnchantment;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.spell_damage.api.enchantment.CustomConditionalEnchantment;
import net.tinyconfig.models.EnchantmentConfig;

public class SpellInfinityEnchantment extends Enchantment implements CustomConditionalEnchantment {
    public EnchantmentConfig config;

    public SpellInfinityEnchantment(Enchantment.Rarity weight, EnchantmentConfig config, EquipmentSlot... slotTypes) {
        super(weight, EnchantmentTarget.BREAKABLE, slotTypes);
        this.config = config;
        this.setCondition(itemStack -> CombatSpells.config.spell_cost_item_allowed && itemStackHasSpell(itemStack));
    }

    private static boolean itemStackHasSpell(ItemStack stack) {
        var object = (Object)stack;
        if (object instanceof SpellCasterItemStack casterItemStack) {
            return casterItemStack.getSpell() != null;
        }
        return false;
    }

    // MARK: Cost

    public int getMaxLevel() {
        if (!CombatSpells.config.spell_cost_item_allowed) {
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

    // MARK: CustomConditionalEnchantment

    private Condition condition;

    @Override
    public void setCondition(Condition condition) {
        this.condition = condition;
    }

    @Override
    public boolean isAcceptableItem(ItemStack stack) {
        if (condition != null) {
            return condition.isAcceptableItem(stack);
        }
        return super.isAcceptableItem(stack);
    }
}
