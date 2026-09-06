package net.spell_engine.internals.cost;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentTarget;
import net.minecraft.enchantment.MendingEnchantment;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.tags.SpellEngineItemTags;
import net.tiny_config.models.EnchantmentConfig;

import java.util.function.Supplier;

/// Spell Infinity: the caster's item-cost (ammo) is not consumed.
///
/// Restored from the legacy 1.20.1 Java enchantment (1.21 defined it as a data-driven enchantment JSON).
/// Eligibility is the `#spell_engine:enchantable/spell_infinity` item tag ({@link SpellEngineItemTags#ENCHANTABLE_SPELL_INFINITY}),
/// the same source the modern datagen emits (at `tags/items/` on 1.20.1).
public class SpellInfinityEnchantment extends Enchantment {
    private final Supplier<EnchantmentConfig> config;

    public SpellInfinityEnchantment(Enchantment.Rarity weight, Supplier<EnchantmentConfig> config, EquipmentSlot... slotTypes) {
        super(weight, EnchantmentTarget.BREAKABLE, slotTypes);
        this.config = config;
    }

    public EnchantmentConfig config() {
        return config.get();
    }

    public static boolean isEligible(ItemStack stack) {
        return stack.isIn(SpellEngineItemTags.ENCHANTABLE_SPELL_INFINITY);
    }

    private static boolean isEnabled() {
        var serverConfig = SpellEngineMod.config;
        return serverConfig == null || serverConfig.spell_cost_item_allowed;
    }

    // MARK: Applicability

    @Override
    public boolean isAcceptableItem(ItemStack stack) {
        return isEnabled() && config().enabled && isEligible(stack);
    }

    /// Forge 47 enchanting-table hook (`IForgeEnchantment#canApplyAtEnchantingTable`); no `@Override`
    /// because the method only exists on Forge's patched `Enchantment`.
    public boolean canApplyAtEnchantingTable(ItemStack stack) {
        return isAcceptableItem(stack);
    }

    // MARK: Cost

    @Override
    public int getMaxLevel() {
        if (!isEnabled() || !config().enabled) {
            return 0;
        }
        return config().max_level;
    }

    @Override
    public int getMinPower(int level) {
        return config().min_cost + (level - 1) * config().step_cost;
    }

    @Override
    public int getMaxPower(int level) {
        return super.getMinPower(level) + 50;
    }

    @Override
    public boolean isTreasure() {
        return false;
    }

    // MARK: Accepting others

    @Override
    protected boolean canAccept(Enchantment other) {
        return !(other instanceof MendingEnchantment) && super.canAccept(other);
    }
}
