package net.spell_engine.config;

import net.spell_engine.api.enchantment.Enchantments_SpellEngine;
import net.tinyconfig.models.EnchantmentConfig;

public class EnchantmentsConfig {
    public EnchantmentConfig infinity = new EnchantmentConfig(1, 20, 30, 1);

    public static EnchantmentsConfig createDefault() {
        return new EnchantmentsConfig();
    }

    public void apply() {
        Enchantments_SpellEngine.INFINITY.config = infinity;
    }
}
