package net.spell_engine.neoforge.compat;

import net.spell_engine.compat.accessories.AccessoriesCompat;

public class NeoForgeCompatFeatures {
    public static void init() {
        initSlotCompat();
    }

    public static void initSlotCompat() {
        AccessoriesCompat.init();
    }
}
