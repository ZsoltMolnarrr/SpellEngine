package net.spell_engine.fabric.compat;

import net.spell_engine.fabric.compat.trinkets.TrinketsCompat;

public class FabricCompatFeatures {
    public static void initialize() {
        initSlotCompat();
    }

    public static void initSlotCompat() {
        TrinketsCompat.init();
    }
}
