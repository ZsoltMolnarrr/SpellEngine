package net.spell_engine.rpg_series;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.item.v1.EnchantmentEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.enchantment.Enchantments;
import net.spell_engine.api.item.weapon.StaffItem;
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
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            LootHelper.configureV2(registries, key.getValue(), tableBuilder, lootEquipmentConfig.value, new HashMap<>());
            LootHelper.configureV2(registries, key.getValue(), tableBuilder, lootScrollsConfig.value, new HashMap<>());
        });
        ServerLifecycleEvents.SERVER_STARTED.register((server) -> {
            LootHelper.updateTagCache(lootEquipmentConfig.value);
        });
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, serverResourceManager, success) -> {
            LootHelper.updateTagCache(lootEquipmentConfig.value);
        });

        var staffEnchantments = Set.of(Enchantments.KNOCKBACK, Enchantments.FIRE_ASPECT, Enchantments.LOOTING);
        EnchantmentEvents.ALLOW_ENCHANTING.register((enchantment, target, enchantingContext) -> {
            if (target.getItem() instanceof StaffItem && staffEnchantments.contains(enchantment.getKey().get())) {
                return TriState.TRUE;
            }
            return TriState.DEFAULT;
        });
    }
}
