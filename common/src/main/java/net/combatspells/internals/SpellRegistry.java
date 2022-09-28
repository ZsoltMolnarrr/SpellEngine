package net.combatspells.internals;

import net.combatspells.api.Spell;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

public class SpellRegistry {
    public static final Map<Identifier, Spell> spells = new HashMap();

    public static void initialize() {
        // spells.put(new Identifier("minecraft", "wooden_sword"), "fireball");

        var fireBall = new Spell();
        fireBall.cast_duration = 2;
        fireBall.on_release = new Spell.Release();
        fireBall.on_release.action = Spell.Release.Action.SHOOT_PROJECTILE;
        fireBall.on_release.projectile = new Spell.ProjectileData();

        spells.put(new Identifier("minecraft", "wooden_sword"), fireBall);
    }
}
