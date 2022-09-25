package net.combatspells.fabric;

import net.fabricmc.loader.api.FabricLoader;
import net.combatspells.Platform;

import static net.combatspells.Platform.Type.FABRIC;

public class PlatformImpl {
    public static Platform.Type getPlatformType() {
        return FABRIC;
    }

    public static boolean isModLoaded(String modid) {
        return FabricLoader.getInstance().isModLoaded(modid);
    }
}
