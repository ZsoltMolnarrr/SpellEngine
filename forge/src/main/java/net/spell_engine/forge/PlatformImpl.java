package net.spell_engine.forge;

import net.minecraftforge.fml.ModList;
import net.spell_engine.Platform;

import static net.spell_engine.Platform.Type.FORGE;

public class PlatformImpl {
    public static Platform.Type getPlatformType() {
        return FORGE;
    }

    public static boolean isModLoaded(String modid) {
        return ModList.get().isLoaded(modid);
    }
}
