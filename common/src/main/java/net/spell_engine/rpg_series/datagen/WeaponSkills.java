package net.spell_engine.rpg_series.datagen;

import net.minecraft.util.Identifier;
import net.spell_engine.api.datagen.SpellBuilder;
import net.spell_engine.api.render.LightEmission;
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
        var spell = SpellBuilder.createMeleeSpell();

        SpellBuilder.Casting.channel(spell, 8F, 20);
        spell.active.cast.movement_speed = 1.1F;
        spell.active.cast.animation = PlayerAnimation.of("spell_engine:two_handed_spin_static");
        spell.active.cast.animation_pitch = false;
        spell.active.cast.animation_spin = -18F;
        spell.active.cast.sound = Sound.withRandomness(SpellEngineSounds.WHIRLWIND.id(), 0F);
        spell.active.cast.particles = new ParticleBatch[]{
                new ParticleBatch(SpellEngineParticles.smoke_medium.id().toString(),
                        ParticleBatch.Shape.WIDE_PIPE, ParticleBatch.Origin.FEET,
                        1, 0.1F, 0.2F),
                new ParticleBatch("campfire_cosy_smoke",
                        ParticleBatch.Shape.WIDE_PIPE, ParticleBatch.Origin.FEET,
                        0.1F, 0.01F, 0.1F)
        };

        spell.release.sound = new Sound(SpellEngineSounds.WEAPON_THROW.id());
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
        var spell = SpellBuilder.createMeleeSpell();

        SpellBuilder.Casting.instant(spell);

        spell.release.sound = new Sound(SpellEngineSounds.WEAPON_CLEAVE.id());
        spell.release.animation = PlayerAnimation.of("spell_engine:weapon_cleave");
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

        SpellBuilder.Cost.cooldown(spell, 8);
        spell.cost.cooldown.attempt_duration = 0.5F;

        return new Entry(id, spell, title, description, null);
    }

    public static Entry GROUND_SLAM = add(GROUND_SLAM());
    private static Entry GROUND_SLAM() {
        var id = Identifier.of(NAMESPACE, "ground_slam");
        var title = "Ground Slam";
        var description = "Leaps into the air and slams into the ground, dealing {damage} damage to nearby enemies.";
        var spell = SpellBuilder.createMeleeSpell();

        SpellBuilder.Casting.cast(spell, 1.0F);
        spell.active.cast.animation = PlayerAnimation.of("spell_engine:weapon_slam_jump");
        spell.active.cast.animation_pitch = false;
        spell.active.cast.start_sound = new Sound(SpellEngineSounds.WEAPON_HAMMER_SWING.id());

        spell.release.animation = PlayerAnimation.of("spell_engine:weapon_slam_end");

        SpellBuilder.Target.aim(spell);
        spell.target.aim.required = false;
        spell.target.aim.reposition_vertically = -1.5F;

        spell.deliver.delay = 2;

        var damage = SpellBuilder.Impacts.damage(1.5F);
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
        spell.area_impact.sound = new Sound(SpellEngineSounds.WEAPON_GROUND_SLAM.id());

        SpellBuilder.Cost.cooldown(spell, 12);
        spell.cost.cooldown.attempt_duration = 0.5F;

        return new Entry(id, spell, title, description, null);
    }

    public static Entry SMASH = add(SMASH());
    private static Entry SMASH() {
        var id = Identifier.of(NAMESPACE, "smash");
        var title = "Smash";
        var description = "Delivers a strike with powerful knockback, disabling shield and item usage of target.";
        var spell = SpellBuilder.createMeleeSpell();

        SpellBuilder.Casting.cast(spell, 0.5F);
        spell.active.cast.animation = PlayerAnimation.of("spell_engine:weapon_mace_uppercut_start");
        spell.active.cast.animation.speed = 1.5F;
        spell.active.cast.animation_pitch = false;
        spell.release.sound = new Sound(SpellEngineSounds.WEAPON_CLEAVE.id());

        SpellBuilder.Target.none(spell);

        var cut_1 = new Spell.Delivery.Melee.Attack();
        cut_1.attack_speed_multiplier = 1.5F;
        cut_1.delay = 0.3F;
        cut_1.hitbox = new Spell.Delivery.Melee.HitBox();
        cut_1.hitbox.arc = 90;
        cut_1.hitbox.height = 0.2F;
        cut_1.hitbox.roll = 15F;
        cut_1.animation = PlayerAnimation.of("spell_engine:weapon_mace_uppercut_end");

        SpellBuilder.Deliver.melee(spell, List.of(cut_1));

        var damage = SpellBuilder.Impacts.damage(0F, 3F);
        var disrupt = SpellBuilder.Impacts.disrupt(true, 2F);
        disrupt.sound = Sound.of(SpellEngineSounds.WEAPON_MACE_SMASH_IMPACT.id());
        spell.impacts = List.of(damage, disrupt);

        SpellBuilder.Cost.cooldown(spell, 10);
        spell.cost.cooldown.attempt_duration = 1F;

        return new Entry(id, spell, title, description, null);
    }

    public static Entry FLURRY = add(FLURRY());
    private static Entry FLURRY() {
        var id = Identifier.of(NAMESPACE, "flurry");
        var title = "Flurry";
        var description = "Hold to unleash a rapid series of strikes, while also gaining momentum.";
        var spell = SpellBuilder.createMeleeSpell();

//        SpellBuilder.Casting.cast(spell, 1F);
        SpellBuilder.Casting.channel(spell, 1.25F, 3);
        spell.active.cast.animation = PlayerAnimation.of("spell_engine:weapon_flurry_2h_charge");
        spell.active.cast.animation.speed = 1.5F;
        spell.active.cast.animation_pitch = false;

        SpellBuilder.Target.none(spell);

        var momentum = 0.8F;

        var cut_1 = new Spell.Delivery.Melee.Attack();
        cut_1.attack_speed_multiplier = 2F;
        cut_1.delay = 0.3F;
        cut_1.hitbox = new Spell.Delivery.Melee.HitBox();
        cut_1.hitbox.arc = 120;
        cut_1.hitbox.height = 0.2F;
        cut_1.hitbox.roll = 45F;
        cut_1.forward_momentum = momentum;
        // cut_1.movement_speed = 0F;
        cut_1.animation = PlayerAnimation.of("spell_engine:weapon_flurry_2h_slash_1");
        cut_1.swing_sound = Sound.of(SpellEngineSounds.WEAPON_CLAYMORE_SWING.id());
        cut_1.impact_sound = Sound.of(SpellEngineSounds.WEAPON_CLAYMORE_IMPACT.id());
        cut_1.animation.speed = 0.8F;

        var cut_2 = new Spell.Delivery.Melee.Attack();
        cut_2.attack_speed_multiplier = 2F;
        cut_1.delay = 0.3F;
        cut_2.hitbox = new Spell.Delivery.Melee.HitBox();
        cut_2.hitbox.arc = 120;
        cut_2.hitbox.height = 0.2F;
        cut_2.hitbox.roll = -45;
        cut_2.forward_momentum = momentum;
        // cut_2.movement_speed = 0.8F;
        cut_2.animation = PlayerAnimation.of("spell_engine:weapon_flurry_2h_slash_2");
        cut_2.swing_sound = Sound.of(SpellEngineSounds.WEAPON_CLAYMORE_SWING.id());
        cut_2.impact_sound = Sound.of(SpellEngineSounds.WEAPON_CLAYMORE_IMPACT.id());
        cut_2.animation.speed = 0.8F;

        SpellBuilder.Deliver.melee(spell, List.of(cut_1, cut_2));

        spell.impacts = List.of();

        SpellBuilder.Cost.cooldown(spell, 12);
        spell.cost.cooldown.proportional = true;

        return new Entry(id, spell, title, description, null);
    }

    public static Entry SWIFT_STRIKES = add(SWIFT_STRIKES());
    private static Entry SWIFT_STRIKES() {
        var id = Identifier.of(NAMESPACE, "swift_strikes");
        var title = "Swift Strikes";
        var description = "Unleash a rapid series of strikes.";
        var spell = SpellBuilder.createMeleeSpell();

        SpellBuilder.Casting.instant(spell);
        SpellBuilder.Target.none(spell);

        var cut_1 = new Spell.Delivery.Melee.Attack();
        cut_1.attack_speed_multiplier = 2F;
        cut_1.delay = 0.3F;
        cut_1.hitbox = new Spell.Delivery.Melee.HitBox();
        cut_1.hitbox.arc = 90;
        cut_1.hitbox.height = 0.2F;
        cut_1.hitbox.roll = 45F;
        cut_1.animation = PlayerAnimation.of("spell_engine:weapon_twinstrike_slash_1");
        cut_1.animation.speed = 1F;
        cut_1.swing_sound = Sound.of(SpellEngineSounds.WEAPON_SWORD_SWING.id());

        var cut_2 = new Spell.Delivery.Melee.Attack();
        cut_2.attack_speed_multiplier = 2F;
        cut_2.delay = 0.3F;
        cut_2.hitbox = new Spell.Delivery.Melee.HitBox();
        cut_2.hitbox.arc = 90;
        cut_2.hitbox.height = 0.2F;
        cut_2.hitbox.roll = -45;
        cut_2.additional_strike_delay = 0.2F;
        cut_2.animation = PlayerAnimation.of("spell_engine:weapon_twinstrike_slash_2");
        cut_2.animation.speed = 1F;
        cut_2.swing_sound = Sound.of(SpellEngineSounds.WEAPON_SWORD_SWING.id());

        SpellBuilder.Deliver.melee(spell, List.of(cut_1, cut_2));

        spell.impacts = List.of();

        SpellBuilder.Cost.cooldown(spell, 8);
        spell.cost.cooldown.attempt_duration = 1F;

        return new Entry(id, spell, title, description, null);
    }

    public static Entry IMPALE = add(IMPALE());
    private static Entry IMPALE() {
        var id = Identifier.of(NAMESPACE, "impale");
        var title = "Impale";
        var description = "Throws your weapon forwards, dealing {damage} damage and powerful knockback.";
        var spell = SpellBuilder.createMeleeSpell();
        spell.range_mechanic = null;
        spell.range = 20;

        SpellBuilder.Casting.cast(spell, 0.75F, "spell_engine:weapon_spearthrow_ready");
        spell.active.cast.animation.speed = 1.5F;
        spell.release.animation = PlayerAnimation.of("spell_engine:weapon_spearthrow_toss");
        spell.release.sound = Sound.of(SpellEngineSounds.WEAPON_SPEAR_THROW.id());
        spell.release.animation.speed = 1.5F;

        SpellBuilder.Target.aim(spell);

        spell.deliver.type = Spell.Delivery.Type.PROJECTILE;
        spell.deliver.projectile = thrown();
        spell.deliver.projectile.projectile.travel_sound = Sound.of(SpellEngineSounds.WEAPON_SPEAR_TRAVEL.id());
        spell.deliver.projectile.launch_properties.velocity = 1.5F;

        var damage = SpellBuilder.Impacts.damage(1.2F, 3F);
        damage.sound = Sound.of(SpellEngineSounds.WEAPON_SPEAR_STAB.id());
        spell.impacts = List.of(damage);

        SpellBuilder.Cost.cooldown(spell, 10);

        return new Entry(id, spell, title, description, null);
    }

    public static Entry FAN_OF_KNIVES = add(FAN_OF_KNIVES());
    private static Entry FAN_OF_KNIVES() {
        var id = Identifier.of(NAMESPACE, "fan_of_knives");
        var title = "Fan of Knives";
        var description = "Throws several of your blades in a cone, dealing {damage} damage, bouncing off terrain up to {bounce} times.";
        var spell = SpellBuilder.createMeleeSpell();
        spell.range_mechanic = null;
        spell.range = 15;

        SpellBuilder.Casting.instant(spell);
        spell.release.animation = PlayerAnimation.of("spell_engine:weapon_dual_throw");
        spell.release.sound = Sound.of(SpellEngineSounds.WEAPON_DAGGER_THROW.id());

        SpellBuilder.Target.aim(spell);
        spell.deliver.type = Spell.Delivery.Type.PROJECTILE;
        spell.deliver.projectile = thrown();
        // spell.deliver.projectile.projectile.travel_sound = Sound.of(SpellEngineSounds.WEAPON_DAGGER_TRAVEL.id());
        spell.deliver.projectile.launch_properties.velocity = 1.2F;
        spell.deliver.projectile.launch_properties.extra_launch_delay = 1;
        spell.deliver.projectile.launch_properties.extra_launch_count = 2;
        spell.deliver.projectile.direction_offsets = new Spell.Delivery.ShootProjectile.DirectionOffset[] {
                new Spell.Delivery.ShootProjectile.DirectionOffset(-15, 0),
                new Spell.Delivery.ShootProjectile.DirectionOffset(0, 0),
                new Spell.Delivery.ShootProjectile.DirectionOffset(15, 0),
                new Spell.Delivery.ShootProjectile.DirectionOffset(-30, 0),
                new Spell.Delivery.ShootProjectile.DirectionOffset(30, 0)
        };
        spell.deliver.projectile.projectile.perks.bounce = 1;

        var damage = SpellBuilder.Impacts.damage(0.8F, 0.5F);
        damage.sound = Sound.of(SpellEngineSounds.WEAPON_DAGGER_IMPACT.id());
        spell.impacts = List.of(damage);

        SpellBuilder.Cost.cooldown(spell, 8);

        return new Entry(id, spell, title, description, null);
    }

    public static Entry THRUST = add(THRUST());
    private static Entry THRUST() {
        var id = Identifier.of(NAMESPACE, "thrust");
        var title = "Thrust";
        var description = "Lunge forward with your weapon, striking all enemies along your path.";
        var spell = SpellBuilder.createMeleeSpell();

        SpellBuilder.Casting.cast(spell, 0.5F, "spell_engine:weapon_thrust_charge");
        spell.active.cast.start_sound = Sound.of(SpellEngineSounds.WEAPON_SHING_A.id());
        SpellBuilder.Target.none(spell);

        var attack = new Spell.Delivery.Melee.Attack();
        attack.attack_speed_multiplier = 1F;
        attack.delay = 0.1F;
        attack.hitbox = new Spell.Delivery.Melee.HitBox();
        attack.hitbox.arc = 160;
        attack.hitbox.height = 0.2F;
        attack.hitbox.width = 0.5F;
        attack.forward_momentum = 2.25F;
        attack.movement_slipperiness = 0.25F;

        attack.additional_strikes = 4;
        attack.additional_strike_delay = 0.15F;
        attack.additional_hits_on_same_target = false;
        attack.animation = PlayerAnimation.of("spell_engine:weapon_thrust_full");
        attack.animation.speed = 1F;
        attack.swing_sound = Sound.of(SpellEngineSounds.WEAPON_THRUST_LAUNCH.id());
        attack.impact_sound = Sound.of(SpellEngineSounds.WEAPON_SICKLE_IMPACT_LARGE.id());

        SpellBuilder.Deliver.melee(spell, List.of(attack));
        spell.deliver.melee.allow_airborne = false;

        SpellBuilder.Cost.cooldown(spell, 12);

        return new Entry(id, spell, title, description, null);
    }

    public static Entry SWIPE = add(SWIPE());
    private static Entry SWIPE() {
        var id = Identifier.of(NAMESPACE, "swipe");
        var title = "Swipe";
        var description = "Slide forward with your weapon, striking all enemies along your path.";
        var spell = SpellBuilder.createMeleeSpell();

        SpellBuilder.Casting.instant(spell);
        SpellBuilder.Target.none(spell);

        var attack = new Spell.Delivery.Melee.Attack();
        attack.attack_speed_multiplier = 1F;
        attack.delay = 0.3F;
        attack.hitbox = new Spell.Delivery.Melee.HitBox();
        attack.hitbox.arc = 160;
        attack.hitbox.length = 0.5F;
        attack.hitbox.height = 0.2F;
        attack.forward_momentum = 1.75F;
        attack.movement_slipperiness = 0.3F;
        attack.delay = 0.1F;
        attack.additional_strikes = 4;
        attack.additional_strike_delay = 0.15F;
        attack.additional_hits_on_same_target = false;
        attack.animation = PlayerAnimation.of("spell_engine:weapon_slash_uncross_swipe");
        attack.animation.speed = 1F;
        attack.swing_sound = Sound.of(SpellEngineSounds.WEAPON_SWIPE_LAUNCH.id());
        attack.impact_sound = Sound.of(SpellEngineSounds.WEAPON_SICKLE_IMPACT_SMALL.id());

        SpellBuilder.Deliver.melee(spell, List.of(attack));
        spell.deliver.melee.allow_airborne = false;

        spell.impacts = List.of();

        SpellBuilder.Cost.cooldown(spell, 8);

        return new Entry(id, spell, title, description, null);
    }

    // Helpers

    private static Spell.Delivery.ShootProjectile thrown() {
        var projectile = new Spell.Delivery.ShootProjectile();

        var projectileData = new Spell.ProjectileData();
        projectileData.homing_angle = 0F;
        projectileData.perks.bounce = 0;

        projectileData.client_data = new Spell.ProjectileData.Client();

        var model = new Spell.ProjectileModel();
        model.use_held_item = true;
        model.light_emission = LightEmission.NONE;
        model.scale = 1F;
        model.rotate_degrees_per_tick = 0;
        model.rotate_degrees_offset = -135F;
        model.orientation = Spell.ProjectileModel.Orientation.ALONG_MOTION;
        projectileData.client_data.model = model;

        projectileData.travel_sound_interval = 8;
        // projectileData.travel_sound = new Sound(RogueSounds.THROW.id());
        projectile.projectile = projectileData;

        return projectile;
    }
}
