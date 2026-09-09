package net.spell_engine.api.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.internals.cost.SpellInfinityEnchantment;
import net.tiny_config.ConfigManager;
import net.tiny_config.models.EnchantmentConfig;

import java.util.LinkedHashMap;
import java.util.Map;

/// Java-registered enchantments of Spell Engine (1.20.1 has no data-driven enchantments).
public class SpellEngineEnchantments {
    /// `config/spell_engine/enchantments.json`
    public static class EnchantmentsConfig {
        public EnchantmentConfig infinity = new EnchantmentConfig(1, 20, 30, 1);
    }

    public static final ConfigManager<EnchantmentsConfig> config = new ConfigManager<>
            ("enchantments", new EnchantmentsConfig())
            .builder()
            .setDirectory(SpellEngineMod.ID)
            .sanitize(true)
            .build();

    public static final Identifier SPELL_INFINITY_ID = new Identifier(SpellEngineMod.ID, "spell_infinity");
    public static final SpellInfinityEnchantment SPELL_INFINITY = new SpellInfinityEnchantment(
            Enchantment.Rarity.VERY_RARE, () -> config.value.infinity, EquipmentSlot.MAINHAND);

    public static final Map<Identifier, Enchantment> all = new LinkedHashMap<>();
    static {
        all.put(SPELL_INFINITY_ID, SPELL_INFINITY);
    }

    /// Idempotent. Fabric: call from mod init; Forge: call from the `ENCHANTMENT` `RegisterEvent`.
    public static void register() {
        enchantmentsToRegister().forEach((id, enchantment) ->
                Registry.register(Registries.ENCHANTMENT, id, enchantment));
    }

    /// Refreshes the config and returns every enchantment that still needs registering, keyed by the id it
    /// registers under. Creation only — nothing is written here, so a loader that registers enchantments
    /// itself (Forge) iterates this instead.
    public static Map<Identifier, Enchantment> enchantmentsToRegister() {
        config.refresh();
        var enchantments = new LinkedHashMap<Identifier, Enchantment>();
        for (var entry : all.entrySet()) {
            if (Registries.ENCHANTMENT.containsId(entry.getKey())) { continue; }
            enchantments.put(entry.getKey(), entry.getValue());
        }
        return enchantments;
    }
}
