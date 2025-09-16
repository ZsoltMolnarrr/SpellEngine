package net.spell_engine.fabric.compat;

import net.spell_engine.SpellEngineMod;
import net.spell_engine.compat.accessories.AccessoriesCompat;
import net.spell_engine.fabric.compat.trinkets.TrinketsCompat;
import net.tiny_config.ConfigManager;

import java.util.Map;
import java.util.function.Supplier;

public class FabricCompatFeatures {
    private static final ConfigManager<FabricCompatConfig> config = new ConfigManager<>
            ("fabric_compatibility", new FabricCompatConfig())
            .builder()
            .setDirectory(SpellEngineMod.ID)
            .sanitize(true)
            .build();
    private static boolean configLoaded = false;
    private static FabricCompatConfig safeConfig() {
        if (!configLoaded) {
            config.refresh();
            configLoaded = true;
        }
        return config.value;
    }

    public static void initialize() {
        initSlotCompat();
    }

    public static void initSlotCompat() {
        Map<String, Supplier<Boolean>> compatLoaders = Map.of(
                AccessoriesCompat.MOD_ID, AccessoriesCompat::init,
                TrinketsCompat.MOD_ID, TrinketsCompat::init
        );
        var preferred = compatLoaders.get(safeConfig().preferred_slot_mod_id);
        if (preferred != null) {
            if (preferred.get()) {
                // Loaded
                return;
            }
        }
        for (var entry : compatLoaders.entrySet()) {
            if (entry.getValue().get()) {
                // Loaded
                return;
            }
        }
    }
}

