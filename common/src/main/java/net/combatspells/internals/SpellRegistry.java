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
                        new ParticleBatch("flame", ParticleBatch.Shape.CIRCLE, ParticleBatch.Origin.CENTER, 3, 0, 0.1F)
                },
                "fire_charge"
        );
        var firballImpact = new Spell.Impact();
        firballImpact.action = new Spell.Impact.Action();
        firballImpact.action.type = Spell.Impact.Action.Type.DAMAGE;
        firballImpact.action.damage = new Spell.Impact.Action.Damage();
        firballImpact.action.damage.attribute = "spelldamage:fire";
        firballImpact.particles = new ParticleBatch[] {
                new ParticleBatch("lava", ParticleBatch.Shape.CIRCLE, ParticleBatch.Origin.CENTER, 30, 0.5F, 3F)
        };
        fireBall.on_impact = new Spell.Impact[] { firballImpact };
        spells.put(new Identifier("minecraft", "wooden_sword"), fireBall);

        var frostbolt = new Spell();
        frostbolt.cast_duration = 2;
        frostbolt.range = 64;
        frostbolt.on_release = new Spell.Release();
        frostbolt.on_release.action = new Spell.Release.Action();
        frostbolt.on_release.action.type = Spell.Release.Action.Type.SHOOT_PROJECTILE;
        frostbolt.on_release.action.projectile = new Spell.ProjectileData();
        frostbolt.on_release.action.projectile.client_data = new Spell.ProjectileData.Client(
                new ParticleBatch[] {
                        new ParticleBatch("snowflake", ParticleBatch.Shape.CIRCLE, ParticleBatch.Origin.CENTER, 3, 0, 0.1F)
                },
                "snowball"
        );
        var frostboltDamage = new Spell.Impact();
        frostboltDamage.action = new Spell.Impact.Action();
        frostboltDamage.action.type = Spell.Impact.Action.Type.DAMAGE;
        frostboltDamage.action.damage = new Spell.Impact.Action.Damage();
        frostboltDamage.action.damage.attribute = "spelldamage:frost";
        frostboltDamage.particles = new ParticleBatch[] {
                new ParticleBatch("snowflake", ParticleBatch.Shape.CIRCLE, ParticleBatch.Origin.FEET, 30, 0.5F, 3F)
        };

        var frostboltSlow = new Spell.Impact();
        frostboltSlow.action = new Spell.Impact.Action();
        frostboltSlow.action.type = Spell.Impact.Action.Type.STATUS_EFFECT;
        frostboltSlow.action.status_effect = new Spell.Impact.Action.StatusEffect();
        frostboltSlow.action.status_effect.effect_id = "slowness";
        frostboltSlow.action.status_effect.amplifier = 1;
        frostboltSlow.action.status_effect.duration = 40;
//        frostboltSlow.particles = new ParticleBatch[] {
//                new ParticleBatch("snowflake", ParticleBatch.Shape.CIRCLE, 30, 0.5F, 3F)
//        };
        frostbolt.on_impact = new Spell.Impact[] { frostboltDamage, frostboltSlow };
        spells.put(new Identifier("minecraft", "stone_sword"), frostbolt);

    }
}
