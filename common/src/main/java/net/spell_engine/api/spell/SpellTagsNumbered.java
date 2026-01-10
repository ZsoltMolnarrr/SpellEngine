package net.spell_engine.api.spell;

import net.minecraft.util.Identifier;
import net.spell_engine.api.tags.SpellTags;

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


    public static void register(String namespace, String path, int number) {
        register(Identifier.of(namespace, SpellTags.SPELL_SCROLL_PREFIX + path), number);
    }
    
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
        int number = 1; // RPG Series
        register("wizards", "arcane", number++);
        register("wizards", "fire", number++);
        register("wizards", "frost", number++);
        register("paladins", "paladin", number++);
        register("paladins", "priest", number++);
        register("archers", "archer", number++);
        register("rogues", "rogue", number++);
        register("rogues", "warrior", number++);

        number = 100; // More RPG Series
        register("archers_expansion", "deadeye", number++);
        register("archers_expansion", "tundra_hunter", number++);
        register("archers_expansion", "war_archer", number++);
        register("berserker_rpg", "berserker", number++);
        register("elemental_wizards_rpg", "aqua", number++);
        register("elemental_wizards_rpg", "terra", number++);
        register("elemental_wizards_rpg", "wind", number++);
        register("forcemaster_rpg", "forcemaster", number++);
        register("witcher_rpg", "base_signs", number++);
        register("witcher_rpg", "enhanced_signs", number++);
        register("witcher_rpg", "fencing", number++);
        register("witcher_rpg", "enhanced_fencing", number++);

        number = 200; // Wompwomp Addons
        register("druids", "druid", number++);
    }
}
