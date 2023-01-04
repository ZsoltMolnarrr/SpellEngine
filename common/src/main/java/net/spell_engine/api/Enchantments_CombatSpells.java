package net.spell_engine.api;

import net.spell_engine.SpellEngineMod;
import net.spell_engine.config.EnchantmentsConfig;
import net.spell_engine.internals.SpellInfinityEnchantment;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

public class Enchantments_CombatSpells {

    // Damage enchants

    public static final String infinityName = "spell_infinity";
    public static final Identifier infinityId = new Identifier(SpellEngineMod.ID, infinityName);
    public static final SpellInfinityEnchantment INFINITY = new SpellInfinityEnchantment(Enchantment.Rarity.VERY_RARE, config().infinity, EquipmentSlot.MAINHAND);

    // Helpers

    public static final Map<Identifier, Enchantment> all;
    static {
        all = new HashMap<>();
        all.put(infinityId, INFINITY);
    }

    private static EnchantmentsConfig config() {
        return SpellEngineMod.enchantmentConfig.value;
    }
}