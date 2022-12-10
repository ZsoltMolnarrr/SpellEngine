package net.spell_engine.fabric;

import net.fabricmc.loader.api.FabricLoader;
import net.spell_engine.Platform;

import static net.spell_engine.Platform.Type.FABRIC;

public class PlatformImpl {
    public static Platform.Type getPlatformType() {
        return FABRIC;
    }

    public static boolean isModLoaded(String modid) {
        return FabricLoader.getInstance().isModLoaded(modid);
    }
}
