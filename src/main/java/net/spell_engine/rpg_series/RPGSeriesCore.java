package net.spell_engine.rpg_series;

import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.spell_engine.api.loot.LootConfig;
import net.spell_engine.api.loot.LootHelper;
import net.spell_engine.rpg_series.config.Defaults;
import net.tinyconfig.ConfigManager;

import java.util.HashMap;

public class RPGSeriesCore {
    private static final String NAMESPACE = "rpg_series";
    public static ConfigManager<LootConfig> lootConfig = new ConfigManager<LootConfig>
            ("loot", Defaults.lootConfig)
            .builder()
            .setDirectory(NAMESPACE)
            .sanitize(true)
            .constrain(LootConfig::constrainValues)
            .build();

    public static void initialize() {
        lootConfig.refresh();
        LootTableEvents.MODIFY.register((resourceManager, lootManager, id, tableBuilder, source) -> {
            LootHelper.configure(id, tableBuilder, lootConfig.value, new HashMap<>());
        });
    }
}
