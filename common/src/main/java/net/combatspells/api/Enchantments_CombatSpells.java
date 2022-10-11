package net.combatspells.api;

import net.combatspells.CombatSpells;
import net.combatspells.config.EnchantmentsConfig;
import net.combatspells.internals.SpellInfinityEnchantment;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

public class Enchantments_CombatSpells {

    // Damage enchants

    public static final String infinityName = "spell_infinity";
    public static final Identifier infinityId = new Identifier(CombatSpells.MOD_ID, infinityName);
    public static final SpellInfinityEnchantment INFINITY = new SpellInfinityEnchantment(Enchantment.Rarity.VERY_RARE, config().infinity, EquipmentSlot.MAINHAND);

    // Helpers

    public static final Map<Identifier, Enchantment> all;
    static {
        all = new HashMap<>();
        all.put(infinityId, INFINITY);
    }

    private static EnchantmentsConfig config() {
        return CombatSpells.enchantmentConfig.value;
    }
}

