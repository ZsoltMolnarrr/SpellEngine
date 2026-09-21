package net.spell_engine.rpg_series;

import net.minecraft.enchantment.Enchantments;
import net.spell_engine.PlatformEvents;
import net.spell_engine.api.item.weapon.StaffItem;
import net.spell_engine.api.util.TriState;
import net.spell_engine.rpg_series.loot.LootConfig;
import net.spell_engine.rpg_series.loot.LootHelper;
import net.spell_engine.rpg_series.config.LootDefaults;
import net.tiny_config.ConfigManager;

import java.util.List;
import java.util.Set;

public class RPGSeriesCore {
    public static final String NAMESPACE = "rpg_series";

    public static ConfigManager<LootConfig> lootEquipmentConfig = new ConfigManager<>
            ("loot_equipment_v3", LootDefaults.itemLootConfig)
            .builder()
            .setDirectory(NAMESPACE)
            .sanitize(true)
            .constrain(config -> LootConfig.constrainValues(config, LootDefaults.itemLootConfig))
            .build();

    public static ConfigManager<LootConfig> lootScrollsConfig = new ConfigManager<>
            ("loot_scrolls_v2", LootDefaults.scrollLootConfig)
            .builder()
            .setDirectory(NAMESPACE)
            .sanitize(true)
            .constrain(config -> LootConfig.constrainValues(config, LootDefaults.scrollLootConfig))
            .build();

    public static ConfigManager<LootConfig> lootMiscConfig = new ConfigManager<>
            ("loot_misc", LootDefaults.miscLootConfig)
            .builder()
            .setDirectory(NAMESPACE)
            .sanitize(true)
            .constrain(config -> LootConfig.constrainValues(config, LootDefaults.miscLootConfig))
            .build();

    private static List<ConfigManager<LootConfig>> lootConfigs() {
        return List.of(lootEquipmentConfig, lootScrollsConfig, lootMiscConfig);
    }

    public static void init() {
        lootEquipmentConfig.refresh();
        lootScrollsConfig.refresh();
        lootMiscConfig.refresh();
        LootHelper.TAG_CACHE.refresh();
        PlatformEvents.onLootTableModify(context -> {
            // Snapshot the table's pools before injecting anything, so the fallback of each config
            // inspects the original content (not the pools added by a config processed earlier)
            var existingPools = context.existingPools();
            LootHelper.configure(context.registries(), context.tableId(), existingPools, context::addPool, lootEquipmentConfig.value, "equipment");
            LootHelper.configure(context.registries(), context.tableId(), existingPools, context::addPool, lootScrollsConfig.value, "scrolls");
            LootHelper.configure(context.registries(), context.tableId(), existingPools, context::addPool, lootMiscConfig.value, "misc");
        });
        PlatformEvents.onServerStarted((server) -> {
            lootConfigs().forEach(config -> LootHelper.updateTagCache(config.value));
            LootHelper.saveFallbackReport();
        });
        PlatformEvents.onDataPackReloadComplete(() -> {
            lootConfigs().forEach(config -> LootHelper.updateTagCache(config.value));
            LootHelper.saveFallbackReport();
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
