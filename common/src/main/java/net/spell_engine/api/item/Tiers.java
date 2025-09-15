package net.spell_engine.api.item;

import net.minecraft.util.Identifier;

public class Tiers {
    /**
     * Automatically determine the tier of a weapon based on its identifier.
     * It is kind of a best guess.
     */
    public static int unsafe(Identifier id) {
        return unsafe(id.getPath());
    }

    /**
     * Automatically determine the tier of a weapon based on its identifier.
     * It is kind of a best guess.
     */
    public static int unsafe(String name) {
        if (name.contains("ruby") || name.contains("aeternium") || name.contains("crystal") || name.contains("smaragdant")) {
            return 4;
        }
        if (name.contains("netherite")) {
            return 3;
        }
        if (name.contains("diamond")) {
            return 2;
        }
        if (name.contains("iron")) {
            return 1;
        }
        return 0;
    }
}
