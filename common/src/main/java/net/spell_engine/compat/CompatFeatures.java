package net.spell_engine.compat;

import net.spell_engine.compat.container.ContainerCompat;
import net.spell_engine.compat.trinkets.TrinketsCompat;

public class CompatFeatures {
    public static void initialize() {
        ContainerCompat.init();
        TrinketsCompat.init();
        CombatRollCompat.init();
        MeleeCompat.init();
        FTBTeamsCompat.init();
    }
}
