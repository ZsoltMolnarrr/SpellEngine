package net.spell_engine.fabric.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.compat.accessories.AccessoriesCompat;
import net.spell_engine.compat.accessories.AccessoriesCompatHeader;
import net.spell_engine.fabric.compat.trinkets.TrinketsCompat;
import net.spell_engine.fabric.compat.trinkets.TrinketsCompatHeader;
import net.tiny_config.ConfigManager;

import java.util.LinkedHashMap;
import java.util.function.Supplier;

public class FabricCompatFeatures {
    private static final ConfigManager<FabricCompatConfig> config = new ConfigManager<>
            ("fabric_compatibility", new FabricCompatConfig())
            .builder()
            .setDirectory(SpellEngineMod.ID)
            .sanitize(true)
            .build();
    private static FabricCompatConfig safeConfig() {
        return config.safeValue();
    }

    public static void initialize() {
        initSlotCompat();
    }

    public static String initSlotCompat() {
        LinkedHashMap<String, Supplier<Boolean>> compatLoaders = new LinkedHashMap<>();
        if (FabricLoader.getInstance().isModLoaded(AccessoriesCompatHeader.MOD_ID)) {
            compatLoaders.put(AccessoriesCompatHeader.MOD_ID, AccessoriesCompat::init);
        }
        if (FabricLoader.getInstance().isModLoaded(TrinketsCompatHeader.MOD_ID)) {
            compatLoaders.put(TrinketsCompatHeader.MOD_ID, TrinketsCompat::init);
        }

        var preferredId = safeConfig().preferred_slot_mod_id;
        var preferred = compatLoaders.get(safeConfig().preferred_slot_mod_id);
        if (preferred != null) {
            compatLoaders.remove(preferredId);
            compatLoaders.putFirst(preferredId, preferred);
        }
        for (var entry : compatLoaders.entrySet()) {
            if (entry.getValue().get()) {
                return entry.getKey();
            }
        }
        return null;
    }
}

