package net.spell_engine.rpg_series;

import net.minecraft.enchantment.Enchantments;
import net.spell_engine.PlatformEvents;
import net.spell_engine.api.item.weapon.StaffItem;
import net.spell_engine.api.util.TriState;
import net.spell_engine.rpg_series.loot.LootConfig;
import net.spell_engine.rpg_series.loot.LootHelper;
import net.spell_engine.rpg_series.config.Defaults;
import net.tiny_config.ConfigManager;

import java.util.HashMap;
import java.util.Set;

public class RPGSeriesCore {
    public static final String NAMESPACE = "rpg_series";

    public static ConfigManager<LootConfig> lootEquipmentConfig = new ConfigManager<>
            ("loot_equipment_v2", Defaults.itemLootConfig)
            .builder()
            .setDirectory(NAMESPACE)
            .sanitize(true)
            .constrain(LootConfig::constrainValues)
            .build();

    public static ConfigManager<LootConfig> lootScrollsConfig = new ConfigManager<>
            ("loot_scrolls_v2", Defaults.scrollLootConfig)
            .builder()
            .setDirectory(NAMESPACE)
            .sanitize(true)
            .constrain(LootConfig::constrainValues)
            .build();

    public static void init() {
        lootEquipmentConfig.refresh();
        lootScrollsConfig.refresh();
        LootHelper.TAG_CACHE.refresh();
        PlatformEvents.onLootTableModify(context -> {
            LootHelper.configure(context.registries(), context.tableId(), context::addPool, lootEquipmentConfig.value, new HashMap<>());
            LootHelper.configure(context.registries(), context.tableId(), context::addPool, lootScrollsConfig.value, new HashMap<>());
        });
        PlatformEvents.onServerStarted((server) -> {
            LootHelper.updateTagCache(lootEquipmentConfig.value);
        });
        PlatformEvents.onDataPackReloadComplete(() -> {
            LootHelper.updateTagCache(lootEquipmentConfig.value);
        });

        var staffEnchantments = Set.of(Enchantments.KNOCKBACK, Enchantments.FIRE_ASPECT, Enchantments.LOOTING);
        PlatformEvents.onAllowEnchanting((enchantment, target) -> {
            if (target.getItem() instanceof StaffItem && staffEnchantments.contains(enchantment.getKey().get())) {
                return TriState.ALLOW;
            }
            return TriState.PASS;
        });
    }
}
