package net.combatspells.internals;

import net.combatspells.api.spell.ParticleBatch;
import net.combatspells.api.spell.Spell;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

public class SpellRegistry {
    public static final Map<Identifier, Spell> spells = new HashMap();

    public static void initialize() {
        // spells.put(new Identifier("minecraft", "wooden_sword"), "fireball");

        var fireBall = new Spell();
        fireBall.cast_duration = 2;
        fireBall.range = 64;
        fireBall.on_release = new Spell.Release();
        fireBall.on_release.action = new Spell.Release.Action();
        fireBall.on_release.action.type = Spell.Release.Action.Type.SHOOT_PROJECTILE;
        fireBall.on_release.action.projectile = new Spell.ProjectileData();
        fireBall.on_release.action.projectile.client_data = new Spell.ProjectileData.Client(
                new ParticleBatch[] {
                        new ParticleBatch("flame", ParticleBatch.Shape.CIRCLE, 3, 0, 0.1F)
                },
//                new ParticleBatch[] {
//                        new ParticleBatch("lava", ParticleBatch.Shape.CIRCLE, 30, 0.5F, 3F)
//                },
                "fire_charge"
        );

        fireBall.on_impact = new Spell.Impact();
        fireBall.on_impact.action = new Spell.Impact.Action();
        fireBall.on_impact.action.type = Spell.Impact.Action.Type.DAMAGE;
        fireBall.on_impact.action.damage = new Spell.Impact.Action.Damage();
        fireBall.on_impact.action.damage.attribute = "spelldamage:fire";
        fireBall.on_impact.particles = new ParticleBatch[] {
                new ParticleBatch("lava", ParticleBatch.Shape.CIRCLE, 30, 0.5F, 3F)
        };

        spells.put(new Identifier("minecraft", "wooden_sword"), fireBall);
    }
}
