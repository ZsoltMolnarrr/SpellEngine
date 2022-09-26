package net.combatspells.internals;

import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

public class SpellRegistry {
    public static final Map<Identifier, String> spells = new HashMap();

    public static void initialize() {
        spells.put(new Identifier("minecraft", "wooden_sword"), "fireball");
    }
}
