package net.spell_engine.api.spell;

import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * An arbitrary number associated with a spell pool.
 * Used for item model predicates.
 * Zero is reserved for null.
 */
public class SpellTagsNumbered {
    public static final int NONE = 0;
    private static final Map<String, Integer> poolNumbers = new HashMap<>();
    public static void register(Identifier id, int number) {
        poolNumbers.put(id.toString(), number);
    }

    public static int get(Identifier id) {
        return poolNumbers.getOrDefault(id.toString(), NONE);
    }

    public static int get(String id) {
        return poolNumbers.getOrDefault(id, NONE);
    }

    public static boolean isRegistered(Identifier id) {
        return poolNumbers.containsKey(id.toString());
    }

    static {
        int number = 1;
        register(Identifier.of("wizards:arcane"), number++);
        register(Identifier.of("wizards:fire"), number++);
        register(Identifier.of("wizards:frost"), number++);
        register(Identifier.of("paladins:paladin"), number++);
        register(Identifier.of("paladins:priest"), number++);
        register(Identifier.of("archers:archer"), number++);
        register(Identifier.of("rogues:rogue"), number++);
        register(Identifier.of("rogues:warrior"), number++);
        register(Identifier.of("archers_expansion:deadeye"), number++);
        register(Identifier.of("archers_expansion:tundra_hunter"), number++);
        register(Identifier.of("archers_expansion:war_archer"), number++);
        register(Identifier.of("berserker_rpg:berserker"), number++);
        register(Identifier.of("elemental_wizards_rpg:aqua"), number++);
        register(Identifier.of("elemental_wizards_rpg:terra"), number++);
        register(Identifier.of("elemental_wizards_rpg:wind"), number++);
        register(Identifier.of("forcemaster_rpg:forcemaster"), number++);
        register(Identifier.of("witcher_rpg:fencing"), number++);
        register(Identifier.of("witcher_rpg:master"), number++);
        register(Identifier.of("witcher_rpg:signs"), number++);
    }
}
