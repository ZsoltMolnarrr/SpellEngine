package net.spell_engine.fabric.compat;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.compat.accessories.AccessoriesCompat;
import net.spell_engine.compat.accessories.AccessoriesCompatHeader;
import net.spell_engine.fabric.compat.trinkets.TrinketsCompat;
import net.spell_engine.fabric.compat.trinkets.TrinketsCompatHeader;
import net.tiny_config.ConfigManager;

import java.util.LinkedHashMap;
import java.util.Objects;
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

    private static String selectedSlotMod = null;
    public static String initSlotCompat() {
        var loadedConfig = safeConfig();
        if (selectedSlotMod != null) {
            return selectedSlotMod;
        }

        LinkedHashMap<String, Supplier<Boolean>> compatLoaders = new LinkedHashMap<>();
        if (FabricLoader.getInstance().isModLoaded(AccessoriesCompatHeader.MOD_ID)) {
            compatLoaders.put(AccessoriesCompatHeader.MOD_ID, AccessoriesCompat::init);
        }
        if (FabricLoader.getInstance().isModLoaded(TrinketsCompatHeader.MOD_ID)) {
            compatLoaders.put(TrinketsCompatHeader.MOD_ID, TrinketsCompat::init);
        }

        var preferredId = loadedConfig.preferred_slot_mod;
        var preferred = compatLoaders.get(loadedConfig.preferred_slot_mod);
        if (preferred != null) {
            compatLoaders.remove(preferredId);
            compatLoaders.putFirst(preferredId, preferred);
        }

        for (var entry : compatLoaders.entrySet()) {
            var modName = entry.getKey();
            var initialized = entry.getValue().get();
            if (initialized) {
                selectedSlotMod = modName;
                var container = FabricLoader.getInstance().getModContainer(SpellEngineMod.ID);
                ResourceManagerHelper.registerBuiltinResourcePack(Identifier.of(SpellEngineMod.ID, modName + "_compat"),
                        container.get(), ResourcePackActivationType.ALWAYS_ENABLED);
                return modName;
            }
        }
        return null;
    }
}