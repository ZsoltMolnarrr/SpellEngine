package net.spell_engine.rpg_series;

import com.google.common.base.Suppliers;
import net.minecraft.world.item.enchantment.Enchantments;
import net.spell_engine.PlatformEvents;
import net.spell_engine.api.item.weapon.StaffItem;
import net.spell_engine.api.util.TriState;
import net.spell_engine.rpg_series.loot.LootConfig;
import net.spell_engine.rpg_series.loot.LootHelper;
import net.spell_engine.rpg_series.config.LootDefaults;
import net.tiny_config.ConfigManager;

import java.util.Set;

public class RPGSeriesCore {
    public static final String NAMESPACE = "rpg_series";

    public static ConfigManager<LootConfig> lootEquipmentConfig = new ConfigManager<>
            ("loot_equipment_v2", LootDefaults.itemLootConfig)
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

    public static void init() {
        lootEquipmentConfig.refresh();
        lootScrollsConfig.refresh();
        LootHelper.TAG_CACHE.refresh();
        PlatformEvents.onLootTableModify(context -> {
            // Snapshot the table's existing pools lazily, only if a fallback needs to inspect them
            var existingPools = Suppliers.memoize(context::existingPools);
            LootHelper.configure(context.registries(), context.tableId(), existingPools, context::addPool, lootEquipmentConfig.value, "equipment");
            LootHelper.configure(context.registries(), context.tableId(), existingPools, context::addPool, lootScrollsConfig.value, "scrolls");
        });
        PlatformEvents.onServerStarted((server) -> {
            LootHelper.updateTagCache(lootEquipmentConfig.value);
            LootHelper.updateTagCache(lootScrollsConfig.value);
            LootHelper.saveFallbackReport();
        });
        PlatformEvents.onDataPackReloadComplete(() -> {
            LootHelper.updateTagCache(lootEquipmentConfig.value);
            LootHelper.updateTagCache(lootScrollsConfig.value);
            LootHelper.saveFallbackReport();
        });

        var staffEnchantments = Set.of(Enchantments.KNOCKBACK, Enchantments.FIRE_ASPECT, Enchantments.LOOTING);
        PlatformEvents.onAllowEnchanting((enchantment, target) -> {
            if (target.getItem() instanceof StaffItem && staffEnchantments.contains(enchantment.unwrapKey().get())) {
                return TriState.ALLOW;
            }
            return TriState.PASS;
        });
    }
}
