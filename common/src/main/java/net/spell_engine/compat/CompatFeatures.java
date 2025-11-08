package net.spell_engine.compat;

import net.spell_engine.compat.container.ContainerCompat;

public class CompatFeatures {
    public static void initialize() {
        ContainerCompat.init();
        CombatRollCompat.init();
        MeleeCompat.init();
        CriticalStrikeCompat.init();
        FTBTeamsCompat.init();
    }
}
