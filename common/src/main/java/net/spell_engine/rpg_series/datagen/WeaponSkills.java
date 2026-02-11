package net.spell_engine.rpg_series.datagen;

import net.minecraft.util.Identifier;
import net.spell_engine.api.datagen.SpellBuilder;
import net.spell_engine.api.spell.ExternalSpellSchools;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.fx.PlayerAnimation;
import net.spell_engine.api.spell.fx.ParticleBatch;
import net.spell_engine.api.spell.fx.Sound;
import net.spell_engine.client.gui.SpellTooltip;
import net.spell_engine.fx.SpellEngineParticles;
import net.spell_engine.fx.SpellEngineSounds;
import net.spell_engine.rpg_series.RPGSeriesCore;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class WeaponSkills {
    public static String NAMESPACE = RPGSeriesCore.NAMESPACE;
    public record Entry(Identifier id, Spell spell, String title, String description,
                        @Nullable SpellTooltip.DescriptionMutator mutator) { }
    public static final List<Entry> entries = new ArrayList<>();
    private static Entry add(Entry entry) {
        entries.add(entry);
        return entry;
    }

    public static final Entry WHIRLWIND = add(whirlwind());
    private static Entry whirlwind() {
        var id = Identifier.of(NAMESPACE, "whirlwind");
        var title = "Whirlwind";
        var description = "Hold to spin around, dealing {damage} damage per second, to nearby enemies.";
        var spell = SpellBuilder.createWeaponSpell();
        spell.school = ExternalSpellSchools.PHYSICAL_MELEE;
        spell.range = 0;
        spell.range_mechanic = Spell.RangeMechanic.MELEE;

        spell.active.cast.duration = 8F;
        spell.active.cast.movement_speed = 1.1F;
        spell.active.cast.animation = PlayerAnimation.of("spell_engine:two_handed_spin_static");
        spell.active.cast.animation_pitch = false;
        spell.active.cast.animation_spin = -18F;
        spell.active.cast.channel_ticks = 8;
        spell.active.cast.sound = Sound.withRandomness(SpellEngineSounds.WHIRLWIND.id(), 0F);
        spell.active.cast.particles = new ParticleBatch[]{
                new ParticleBatch(SpellEngineParticles.smoke_medium.id().toString(),
                        ParticleBatch.Shape.WIDE_PIPE, ParticleBatch.Origin.FEET,
                        1, 0.1F, 0.2F),
                new ParticleBatch("campfire_cosy_smoke",
                        ParticleBatch.Shape.WIDE_PIPE, ParticleBatch.Origin.FEET,
                        0.1F, 0.01F, 0.1F)
        };

        spell.release.sound = new Sound(SpellEngineSounds.THROW_WEAPON.id());
        spell.release.particles = new ParticleBatch[]{
                new ParticleBatch(SpellEngineParticles.smoke_medium.id().toString(),
                        ParticleBatch.Shape.CIRCLE, ParticleBatch.Origin.FEET,
                        25, 0.15F, 0.15F)
                        .preSpawnTravel(1)
        };

        spell.target.type = Spell.Target.Type.AREA;
        spell.target.area = new Spell.Target.Area();
        spell.target.area.vertical_range_multiplier = 0.25F;

        var damage = new Spell.Impact();
        damage.action = new Spell.Impact.Action();
        damage.action.type = Spell.Impact.Action.Type.DAMAGE;
        damage.action.damage = new Spell.Impact.Action.Damage();
        damage.action.damage.spell_power_coefficient = 1.2F;
        damage.action.damage.knockback = 0.8F;
        damage.particles = new ParticleBatch[]{
                new ParticleBatch("crit",
                        ParticleBatch.Shape.SPHERE, ParticleBatch.Origin.CENTER,
                        30, 0.2F, 0.7F)
        };
        spell.impacts = List.of(damage);

        SpellBuilder.Cost.cooldown(spell, 30);
        spell.cost.cooldown.proportional = true;
        spell.cost.exhaust = 0.5F;

        return new Entry(id, spell, title, description, null);
    }

    public static Entry CLEAVE = add(CLEAVE());
    private static Entry CLEAVE() {
        var id = Identifier.of(NAMESPACE, "cleave");
        var title = "Cleave";
        var description = "Performs a spin attack, dealing {damage} damage to nearby enemies.";
        var spell = SpellBuilder.createWeaponSpell();
        spell.school = ExternalSpellSchools.PHYSICAL_MELEE;
        spell.range = 0;
        spell.range_mechanic = Spell.RangeMechanic.MELEE;

        SpellBuilder.Casting.instant(spell);

        spell.release.sound = new Sound(SpellEngineSounds.CLEAVE.id());
        spell.release.animation = PlayerAnimation.of("spell_engine:cleave");
        spell.active.cast.animation_pitch = false;

        spell.target.type = Spell.Target.Type.AREA;
        spell.target.area = new Spell.Target.Area();
        spell.target.area.distance_dropoff = Spell.Target.Area.DropoffCurve.NONE;
        spell.target.area.vertical_range_multiplier = 0.5F;

        spell.release.particles_scaled_with_ranged = new ParticleBatch[]{
                new ParticleBatch(SpellEngineParticles.area_swirl.id().toString(),
                        ParticleBatch.Shape.SPHERE, ParticleBatch.Origin.CENTER,
                        1, 0.0F, 0.F)
                        .scale(0.8F)
                        .followEntity(true),
                new ParticleBatch(SpellEngineParticles.smoke_medium.id().toString(),
                        ParticleBatch.Shape.CIRCLE, ParticleBatch.Origin.FEET,
                        25, 0.15F, 0.15F)
                        .preSpawnTravel(1)
        };

        spell.deliver.delay = 2;

        var damage = SpellBuilder.Impacts.damage(1F);
        spell.impacts = List.of(damage);

        SpellBuilder.Cost.cooldown(spell, 6);
        spell.cost.cooldown.attempt_duration = 0.5F;

        return new Entry(id, spell, title, description, null);
    }

    public static Entry GROUND_SLAM = add(GROUND_SLAM());
    private static Entry GROUND_SLAM() {
        var id = Identifier.of(NAMESPACE, "ground_slam");
        var title = "Ground Slam";
        var description = "Leaps into the air and slams into the ground, dealing {damage} damage to nearby enemies.";
        var spell = SpellBuilder.createWeaponSpell();
        spell.school = ExternalSpellSchools.PHYSICAL_MELEE;
        spell.range = 0;
        spell.range_mechanic = Spell.RangeMechanic.MELEE;

        SpellBuilder.Casting.cast(spell, 1.0F);
        spell.active.cast.animation = PlayerAnimation.of("spell_engine:slam_jump");
        spell.active.cast.start_sound = new Sound(SpellEngineSounds.HAMMER_SWING.id());

        spell.release.animation = PlayerAnimation.of("spell_engine:slam_end");
        spell.active.cast.animation_pitch = false;

        spell.target.type = Spell.Target.Type.AIM;
        spell.target.aim = new Spell.Target.Aim();
        spell.target.aim.required = false;
        spell.target.aim.reposition_vertically = -1.5F;

        spell.deliver.delay = 2;

        var damage = SpellBuilder.Impacts.damage(1F);
        spell.impacts = List.of(damage);

        spell.area_impact = new Spell.AreaImpact();
        spell.area_impact.radius = 3F;
        spell.area_impact.particles = new ParticleBatch[]{
                new ParticleBatch(SpellEngineParticles.smoke_medium.id().toString(),
                        ParticleBatch.Shape.CIRCLE, ParticleBatch.Origin.FEET,
                        25, 0.2F, 0.2F)
                        .preSpawnTravel(1),
                new ParticleBatch(SpellEngineParticles.smoke_medium.id().toString(),
                        ParticleBatch.Shape.CIRCLE, ParticleBatch.Origin.FEET,
                        25, 0.3F, 0.3F)
                        .preSpawnTravel(2),
                new ParticleBatch(SpellEngineParticles.smoke_medium.id().toString(),
                        ParticleBatch.Shape.CIRCLE, ParticleBatch.Origin.FEET,
                        25, 0.4F, 0.4F)
                        .preSpawnTravel(4)
        };
        spell.area_impact.sound = new Sound(SpellEngineSounds.GROUND_SLAM.id());

        SpellBuilder.Cost.cooldown(spell, 12);
        spell.cost.cooldown.attempt_duration = 0.5F;

        return new Entry(id, spell, title, description, null);
    }

    public static Entry SLAM = add(SLAM());
    private static Entry SLAM() {
        var id = Identifier.of(NAMESPACE, "slam");
        var title = "Slam";
        var description = "Delivers a powerful overhead strike, dealing {damage} damage and slowing the target for {duration} seconds.";
        var spell = SpellBuilder.createWeaponSpell();
        spell.school = ExternalSpellSchools.PHYSICAL_MELEE;
        spell.range = 0;
        spell.range_mechanic = Spell.RangeMechanic.MELEE;

        SpellBuilder.Casting.cast(spell, 0.8F);
        spell.active.cast.animation = PlayerAnimation.of("spell_engine:one_handed_charge_weapon_spin_v2");

        spell.release.sound = new Sound(SpellEngineSounds.CLEAVE.id());
        spell.release.animation = PlayerAnimation.of("bettercombat:one_handed_uppercut_right");

        spell.target.type = Spell.Target.Type.AIM;
        spell.target.aim = new Spell.Target.Aim();
        spell.target.aim.sticky = true;
        spell.target.aim.required = true;

        var damage = SpellBuilder.Impacts.damage(1F);
        spell.impacts = List.of(damage);

        SpellBuilder.Cost.cooldown(spell, 6);
        spell.cost.cooldown.attempt_duration = 0.5F;

        return new Entry(id, spell, title, description, null);
    }

    public static Entry CRUSHING_BLOW = add(CRUSHING_BLOW());
    private static Entry CRUSHING_BLOW() {
        var id = Identifier.of(NAMESPACE, "crushing_blow");
        var title = "Crushing Blow";
        var description = "Delivers a powerful overhead strike, dealing {damage} damage and slowing the target for {duration} seconds.";
        var spell = SpellBuilder.createWeaponSpell();
        spell.school = ExternalSpellSchools.PHYSICAL_MELEE;
        spell.range = 0;
        spell.range_mechanic = Spell.RangeMechanic.MELEE;

        SpellBuilder.Casting.instant(spell);

        spell.release.sound = new Sound(SpellEngineSounds.CLEAVE.id());
        spell.release.animation = PlayerAnimation.of("bettercombat:one_handed_uppercut_right");
        spell.active.cast.animation_pitch = false;

        spell.target.type = Spell.Target.Type.AREA;
        spell.target.area = new Spell.Target.Area();
        spell.target.area.angle_degrees = 90F;
        spell.target.area.distance_dropoff = Spell.Target.Area.DropoffCurve.NONE;
        spell.target.area.vertical_range_multiplier = 0.5F;

        spell.release.particles_scaled_with_ranged = new ParticleBatch[]{
        };

        spell.deliver.delay = 2;

        var damage = SpellBuilder.Impacts.damage(1F, 2F);
        spell.impacts = List.of(damage);

        SpellBuilder.Cost.cooldown(spell, 6);
        spell.cost.cooldown.attempt_duration = 0.5F;

        return new Entry(id, spell, title, description, null);
    }

    public static Entry FLURRY = add(FLURRY());
    private static Entry FLURRY() {
        var id = Identifier.of(NAMESPACE, "flurry");
        var title = "Flurry";
        var description = "Unleash a rapid series of strikes.";
        var spell = SpellBuilder.createWeaponSpell();
        spell.school = ExternalSpellSchools.PHYSICAL_MELEE;
        spell.range = 0;
        spell.range_mechanic = Spell.RangeMechanic.MELEE;

        SpellBuilder.Casting.instant(spell);
        spell.target = new Spell.Target();
        spell.target.type = Spell.Target.Type.CASTER;

        var cut_1 = new Spell.Impact.Action.Melee.Attack();
        cut_1.attack_speed_multiplier = 1.5F;
        cut_1.delay = 0.3F;
        cut_1.hitbox_arc = 90;
        cut_1.hitbox = new Spell.Impact.Action.Melee.HitBox();
        cut_1.hitbox.height = 0.2F;
        cut_1.forward_momentum = 1F;
        cut_1.animation = PlayerAnimation.of("spell_engine:flurry_2h_slash1");

        var cut_2 = new Spell.Impact.Action.Melee.Attack();
        cut_2.attack_speed_multiplier = 1.5F;
        cut_2.delay = 0.3F;
        cut_2.hitbox_arc = 90;
        cut_2.hitbox = new Spell.Impact.Action.Melee.HitBox();
        cut_2.hitbox.height = 0.2F;
        cut_2.forward_momentum = 2F;
        cut_2.animation = PlayerAnimation.of("spell_engine:flurry_2h_slash2");

        spell.impacts = List.of(
                SpellBuilder.Impacts.melee(List.of(cut_1, cut_2))
        );

        // SpellBuilder.Cost.cooldown(spell, 10);
        spell.cost.cooldown.attempt_duration = 1F;

        return new Entry(id, spell, title, description, null);
    }
}
