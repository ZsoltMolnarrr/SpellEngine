package net.spell_engine.neoforge;

import net.neoforged.fml.ModList;
import net.spell_engine.Platform;

public class PlatformImpl {
    public static Platform.Type getPlatformType() {
        return Platform.Type.NEOFORGE;
    }

    public static class NeoForgeUtil implements Platform.Util {
        @Override
        public boolean isModLoaded(String modid) {
            return ModList.get().isLoaded(modid);
        }
    }
    private static final Platform.Util UTIL = new NeoForgeUtil();
    public static Platform.Util util() {
        return UTIL;
    }
}
