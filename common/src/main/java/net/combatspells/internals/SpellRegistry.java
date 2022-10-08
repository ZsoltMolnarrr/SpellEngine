package net.combatspells.internals;

import net.combatspells.api.spell.ParticleBatch;
import net.combatspells.api.spell.Sound;
import net.combatspells.api.spell.Spell;
import net.minecraft.util.Identifier;
import net.spelldamage.api.MagicSchool;

import java.util.HashMap;
import java.util.Map;

public class SpellRegistry {
    public static final Map<Identifier, Spell> spells = new HashMap();

    public static void initialize() {
        // spells.put(new Identifier("minecraft", "wooden_sword"), "fireball");

        var fireBall = new Spell();
        fireBall.cast.duration = 1;
        fireBall.cast.animation = "combatspells:one_handed_staff_forward";
        fireBall.cast.sound = new Sound("combatspells:casting_fire");
        fireBall.range = 64;
        fireBall.icon_id = "combatspells:textures/spells/fireball.png";
        fireBall.school = MagicSchool.FIRE;
        fireBall.on_release = new Spell.Release();
        fireBall.on_release.sound = new Sound("combatspells:release_fire");
        fireBall.on_release.target = new Spell.Release.Target();
        fireBall.on_release.target.type = Spell.Release.Target.Type.PROJECTILE;
        fireBall.on_release.target.projectile = new Spell.ProjectileData();
        fireBall.on_release.target.projectile.client_data = new Spell.ProjectileData.Client(
                new ParticleBatch[] {
                        new ParticleBatch("flame", ParticleBatch.Shape.CIRCLE, ParticleBatch.Origin.CENTER, 3, 0, 0.1F)
                },
                "fire_charge"
        );

        fireBall.on_release.target.projectile.homing_angle = 1;
        fireBall.on_release.target.projectile.velocity = 0.5F;

        var firballImpact = new Spell.Impact();
        firballImpact.sound = new Sound("combatspells:impact_fireball");
        firballImpact.action = new Spell.Impact.Action();
        firballImpact.action.type = Spell.Impact.Action.Type.DAMAGE;
        firballImpact.action.damage = new Spell.Impact.Action.Damage();
        firballImpact.particles = new ParticleBatch[] {
                new ParticleBatch("lava", ParticleBatch.Shape.CIRCLE, ParticleBatch.Origin.CENTER, 30, 0.5F, 3F)
        };
        fireBall.on_impact = new Spell.Impact[] { firballImpact };
        spells.put(new Identifier("minecraft", "wooden_sword"), fireBall);

        var frostbolt = new Spell();
        frostbolt.school = MagicSchool.FROST;
        frostbolt.icon_id = "combatspells:textures/spells/frostbolt.png";
        frostbolt.cast.duration = 1;
        frostbolt.cast.animation = "combatspells:one_handed_staff_forward";
        frostbolt.cast.sound = new Sound("combatspells:casting_frost");
        frostbolt.range = 64;
        frostbolt.on_release = new Spell.Release();
        frostbolt.on_release.sound = new Sound("combatspells:release_frost");
        frostbolt.on_release.target = new Spell.Release.Target();
        frostbolt.on_release.target.type = Spell.Release.Target.Type.PROJECTILE;
        frostbolt.on_release.target.projectile = new Spell.ProjectileData();
        frostbolt.on_release.target.projectile.client_data = new Spell.ProjectileData.Client(
                new ParticleBatch[] {
                        new ParticleBatch("snowflake", ParticleBatch.Shape.CIRCLE, ParticleBatch.Origin.CENTER, 3, 0, 0.1F)
                },
                "snowball"
        );

        frostbolt.on_release.target.projectile.homing_angle = 2;
        frostbolt.on_release.target.projectile.velocity = 1F;

        var frostboltImpact = new Spell.Impact();
        frostboltImpact.sound = new Sound("combatspells:impact_frostbolt");
        frostboltImpact.action = new Spell.Impact.Action();
        frostboltImpact.action.type = Spell.Impact.Action.Type.DAMAGE;
        frostboltImpact.action.damage = new Spell.Impact.Action.Damage();
        frostboltImpact.particles = new ParticleBatch[] {
                new ParticleBatch("soul_fire_flame", ParticleBatch.Shape.CIRCLE, ParticleBatch.Origin.CENTER, 30, 0.5F, 3F)
        };
        frostbolt.on_impact = new Spell.Impact[] { frostboltImpact };
        spells.put(new Identifier("minecraft", "diamond_sword"), frostbolt);


        var scorch = new Spell();
        scorch.school = MagicSchool.FIRE;
        scorch.cast.duration = 0.5F;
        scorch.range = 32;
        scorch.on_release = new Spell.Release();
        scorch.on_release.target = new Spell.Release.Target();
        scorch.on_release.target.type = Spell.Release.Target.Type.CURSOR;
        scorch.on_release.target.cursor = new Spell.Release.Target.Cursor();
        var scorchDamage = new Spell.Impact();
        scorchDamage.action = new Spell.Impact.Action();
        scorchDamage.action.type = Spell.Impact.Action.Type.DAMAGE;
        scorchDamage.action.damage = new Spell.Impact.Action.Damage();
        scorchDamage.particles = new ParticleBatch[] {
                new ParticleBatch("small_flame", ParticleBatch.Shape.CIRCLE, ParticleBatch.Origin.CENTER, 30, 0.2F, 0.2F)
        };
        scorch.on_impact = new Spell.Impact[] { scorchDamage };
        spells.put(new Identifier("minecraft", "golden_sword"), scorch);


        var frostNova = new Spell();
        frostNova.school = MagicSchool.FROST;
        frostNova.cast.duration = 2;
        frostNova.cooldown_duration = 3;
        frostNova.range = 10;
        frostNova.on_release = new Spell.Release();
        frostNova.on_release.target = new Spell.Release.Target();
        frostNova.on_release.target.type = Spell.Release.Target.Type.AREA;
        frostNova.on_release.target.area = new Spell.Release.Target.Area();
        frostNova.on_release.target.area.vertical_range_multiplier = 0.3F;
        frostNova.on_release.particles = new ParticleBatch[] {
                new ParticleBatch("snowflake", ParticleBatch.Shape.CIRCLE, ParticleBatch.Origin.FEET, 50, 0.2F, 0.2F),
                new ParticleBatch("snowflake", ParticleBatch.Shape.CIRCLE, ParticleBatch.Origin.FEET, 50, 0.4F, 0.4F),
                new ParticleBatch("snowflake", ParticleBatch.Shape.CIRCLE, ParticleBatch.Origin.FEET, 50, 0.6F, 0.6F),
                new ParticleBatch("soul_fire_flame", ParticleBatch.Shape.CIRCLE, ParticleBatch.Origin.FEET, 30, 0.0F, 0.4F),
        };

        var frostDamage = new Spell.Impact();
        frostDamage.action = new Spell.Impact.Action();
        frostDamage.action.type = Spell.Impact.Action.Type.DAMAGE;
        frostDamage.action.damage = new Spell.Impact.Action.Damage();
        frostDamage.particles = new ParticleBatch[] {
                new ParticleBatch("snowflake", ParticleBatch.Shape.CIRCLE, ParticleBatch.Origin.FEET, 30, 0.5F, 3F)
        };
        var frostSlow = new Spell.Impact();
        frostSlow.action = new Spell.Impact.Action();
        frostSlow.action.type = Spell.Impact.Action.Type.STATUS_EFFECT;
        frostSlow.action.status_effect = new Spell.Impact.Action.StatusEffect();
        frostSlow.action.status_effect.effect_id = "slowness";
        frostSlow.action.status_effect.amplifier = 1;
        frostSlow.action.status_effect.duration = 40;
//        frostSlow.particles = new ParticleBatch[] {
//                new ParticleBatch("snowflake", ParticleBatch.Shape.CIRCLE, 30, 0.5F, 3F)
//        };
        frostNova.on_impact = new Spell.Impact[] { frostDamage, frostSlow };

        spells.put(new Identifier("minecraft", "stone_sword"), frostNova);
    }
}
