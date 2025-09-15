package net.spell_engine.fabric;

import net.fabricmc.loader.api.FabricLoader;
import net.spell_engine.Platform;

public class PlatformImpl {
    public static Platform.Type getPlatformType() {
        return Platform.Type.FABRIC;
    }

    public static class FabricUtil implements Platform.Util {
        @Override
        public boolean isModLoaded(String modid) {
            return FabricLoader.getInstance().isModLoaded(modid);
        }
    }
    private static final Platform.Util UTIL = new FabricUtil();
    public static Platform.Util util() {
        return UTIL;
    }
}
