package net.combatspells.forge;

import net.minecraftforge.fml.ModList;
import net.combatspells.Platform;

import static net.combatspells.Platform.Type.FORGE;

public class PlatformImpl {
    public static Platform.Type getPlatformType() {
        return FORGE;
    }

    public static boolean isModLoaded(String modid) {
        return ModList.get().isLoaded(modid);
    }
}
