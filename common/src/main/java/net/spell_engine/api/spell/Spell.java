package net.spell_engine.api.spell;

import net.minecraft.entity.EquipmentSlot;
import net.spell_engine.api.render.LightEmission;
import net.spell_engine.api.spell.fx.PlayerAnimation;
import net.spell_engine.api.spell.fx.ParticleBatch;
import net.spell_engine.api.spell.fx.Sound;
import net.spell_engine.api.util.AlwaysGenerate;
import net.spell_engine.api.util.NeverGenerate;
import net.spell_engine.api.util.TriState;
import net.spell_engine.internals.target.SpellTarget;
import net.spell_power.api.SpellSchool;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class Spell {
    public SpellSchool school;

    public enum ExtendedArchetype { ARCHERY, MAGIC, MELEE, ANY }
    @Nullable public ExtendedArchetype secondary_archetype = null;
    public float range = 50;
    /// Provide a value for a non-static range mechanic
    @Nullable public RangeMechanic range_mechanic;
    public enum RangeMechanic { MELEE }

    /// Group classifier
    /// Can be any arbitrary string, commonly used: `primary` (recommended for main attack or healing spells)
    /// Spells with the same group override each other, prioritized by tier and rank
    @Nullable public String group;
    /// Quality classifier, used for sorting spells, in an increasing order
    @AlwaysGenerate
    public int tier = 1;
    /// Secondary quality classifier, used for sorting spells, in an increasing order
    @Deprecated
    public int sub_tier = 1;

    /// If this can be obtained from Spell Binding Table, provide an object
    @Nullable public Learn learn;
    public static class Learn { public Learn() {}
        public int level_cost_per_tier = 3;
        public int level_requirement_per_tier = 10;
    }

    @Nullable public Tooltip tooltip;
    public Tooltip tooltip() { return tooltip != null ? tooltip : Tooltip.DEFAULT; }
    public static class Tooltip { public Tooltip() { }; public static final Tooltip DEFAULT = new Tooltip();
        public boolean show_header = true;
        public boolean show_activation = true;
        public boolean show_range = true;
        public LineOptions name = new LineOptions(true, true);
        public LineOptions description = new LineOptions(false, true);
        public static class LineOptions { public LineOptions() { }
            /// Vanilla enum Formatting value by name
            public String color = "GRAY";
            public boolean show_in_compact = true;
            public boolean show_in_details = true;
            public LineOptions(boolean show_in_compact, boolean show_in_details) {
                this.show_in_compact = show_in_compact;
                this.show_in_details = show_in_details;
            }
        }
    }

    public Type type = Type.ACTIVE;
    public enum Type { ACTIVE, PASSIVE, MODIFIER }

    public Active active;
    public static class Active {
        public Cast cast = new Cast();
        public static class Cast { public Cast() { }
            public boolean haste_affected = true;
            public float duration = 0;
            /// The number of times the spell should perform delivery during casting.
            /// If greater than zero, the spell is considered as "channeled", and the deliveries are evenly distributed during the casting duration.
            public int channel_ticks = 0;

            public PlayerAnimation animation;
            public boolean animation_pitch = true;
            public float animation_spin = 0F;
            public boolean animates_ranged_weapon = false;

            /// Default `0.2` matches the same as movement speed during vanilla item usage (such as bow)
            public float movement_speed = 0.2F;
            public Sound start_sound;
            public Sound sound;
            public ParticleBatch[] particles = new ParticleBatch[]{};
        }
    }

    public Passive passive;
    public static class Passive {
        public List<Trigger> triggers = List.of();
    }

    public List<Modifier> modifiers = List.of();
    public static class Modifier {
        /// Universal pattern matcher, against spell ID
        public String spell_pattern;
        /// Bonus to add to the range of the spell
        public float range_add = 0;
        public enum ImpactListModifier {
            PREPEND, /// Adds the impacts to the start of the list
            APPEND /// Adds the impacts to the end of the list
        }
        @Nullable public ImpactListModifier mutate_impacts;
        public List<Impact> impacts = List.of();
        @Nullable public AreaImpact replacing_area_impact;

        /// Determines which impacts to apply, the changes below
        public List<ImpactFilter> impact_filters = List.of();
        public static class ImpactFilter {
            @Nullable public SpellSchool school;
            @Nullable public Spell.Impact.Action.Type type;
        }

        @Nullable public LaunchProperties projectile_launch;
        @Nullable public ProjectileData.Perks projectile_perks;
        @Nullable public Impact.Modifier power_modifier;
        public int channel_ticks_add = 0;
        public float knockback_multiply_base = 0;
        public float spawn_duration_add = 0;
        public int effect_amplifier_add = 0;
        public int effect_amplifier_cap_add = 0;
        public int stash_amplifier_add = 0;
        public float effect_duration_add = 0;
        public float cooldown_duration_deduct = 0;

        public float melee_momentum_add = 0;
        public float melee_slipperiness_add = 0;
        /// Melee delivery - damage multiplier base, applied to all attacks of the melee delivery, example value: `0.5F` for +50% of the total damage.
        public float melee_damage_multiplier = 0F;
        /// Melee delivery - attacks to append
        @Nullable public List<Delivery.Melee.Attack> melee_attacks = null;

        /// Additional cloud or entity spawn placements
        public List<EntityPlacement> additional_placements = List.of();
    }

    public Release release = new Release();
    public static class Release { public Release() { }
        public PlayerAnimation animation;
        public ParticleBatch[] particles;
        public ParticleBatch[] particles_scaled_with_ranged;
        public Sound sound;
    }

    public Target target = new Target();
    public static class Target {
        public Type type = Type.CASTER;
        public enum Type {
            NONE, CASTER, AIM, BEAM, AREA, FROM_TRIGGER
        }
        // The number of maximum targets, applied when greater than zero
        public int cap = 0;

        public Aim aim;
        public static class Aim { public Aim() { }
            /// Whether an entity must be targeted to cast the spell
            public boolean required = false;
            /// Whether the spell casting process keeps an entity that was targeted already
            public boolean sticky = false;
            /// Whether the spell casting process uses the caster as a fallback target
            public boolean use_caster_as_fallback = false;
            /// Vertical repositioning of the aimed position (from cursor),
            /// ignored if an entity is targeted, respects ground
            public float reposition_vertically = 0F;
        }

        public Beam beam;
        public static class Beam {
            public Beam() {

            }
            public enum Luminance { LOW, MEDIUM, HIGH }
            public Beam.Luminance luminance = Beam.Luminance.HIGH;
            public String texture_id = "textures/entity/beacon_beam.png";
            public long color_rgba = 0xFFFFFFFFL;
            public long inner_color_rgba = 0xFFFFFFFFL;
            public float width = 0.1F;
            public float flow = 1;
            public ParticleBatch[] block_hit_particles = new ParticleBatch[]{};
        }

        public Area area;
        public static class Area { public Area() { }
            public enum DropoffCurve { NONE, SQUARED }
            public DropoffCurve distance_dropoff = DropoffCurve.NONE;
            public float horizontal_range_multiplier = 1F;
            public float vertical_range_multiplier = 1F;
            public float angle_degrees = 0F;
            public boolean include_caster = false;
        }
    }

    public Delivery deliver = new Delivery();
    public static class Delivery {
        public Type type = Type.DIRECT;
        public enum Type {
            DIRECT, PROJECTILE, METEOR, CLOUD, SHOOT_ARROW, AFFECT_ARROW, MELEE, STASH_EFFECT, CUSTOM
        }
        public int delay = 0;

        public ShootProjectile projectile;
        public static class ShootProjectile {
            public boolean inherit_shooter_velocity = false;
            public boolean inherit_shooter_yaw = true;
            public boolean inherit_shooter_pitch = true;
            public static class DirectionOffset { public DirectionOffset() { }
                public float yaw = 0; public float pitch = 0;
                public DirectionOffset(float yaw, float pitch) { this.yaw = yaw; this.pitch = pitch; }
            }
            public ShootProjectile.DirectionOffset[] direction_offsets;
            public boolean direction_offsets_require_target = false;
            /// Turns the projectile immediately towards the target
            public boolean direct_towards_target = false;
            /// Launch properties of the spell projectile
            public LaunchProperties launch_properties = new LaunchProperties();
            /// The projectile to be launched
            public ProjectileData projectile;
        }

        public Meteor meteor;
        public static class Meteor { public Meteor() { }
            /// How high the falling projectile is launched from compared to the position of the target
            public float launch_height = 10;
            public int offset_requires_sequence = 1;
            public int divergence_requires_sequence = 1;
            public int follow_target_requires_sequence = -1;
            /// How far horizontally the falling projectile is launched from the target
            public float launch_radius = 0;
            /// Launch properties of the falling projectile
            public LaunchProperties launch_properties = new LaunchProperties();
            /// The projectile to be launched
            public ProjectileData projectile;
        }

        public ShootArrow shoot_arrow;
        public static class ShootArrow { public ShootArrow() { }
            public boolean consume_arrow = true;
            public float divergence = 5F;
            public boolean arrow_critical_strike = true;
            /// Launch properties of the arrow
            /// (vanilla default velocity for crossbows is 3.15)
            public LaunchProperties launch_properties = new LaunchProperties().velocity(3.15F);
        }

        public AffectArrow affect_arrow;
        public static class AffectArrow { public AffectArrow() { }
        }

        public Melee melee;
        public static class Melee { public Melee() { }
            /// Whether the melee attacks can be started to perform while airborne,
            /// if false, the attack will be delayed until the caster is on the ground
            public boolean allow_airborne = true;
            public List<Attack> attacks = List.of();
            public static class Attack { public Attack() { }
                /// Only for internal use, do not touch this :)
                @NeverGenerate
                public String id = UUID.randomUUID().toString();
                /// Total damage additive multiplier. Example value: 0.5F for +50% of the total damage.
                public float damage_bonus = 0F;
                /// Duration of the melee attack (in ticks), if zero deferring to use attack cooldown duration (vanilla attack speed).
                public int duration = 0;
                /// A multiplier applied to animation, and non-static duration
                public float attack_speed_multiplier = 1F;
                /// Delay before strike (aka windup), actual value: multiplied by duration, rounded to whole ticks.
                public float delay = 0.25F;
                /// Whether additional melee attack should be performed in a row, with `additional_strike_delay` delay between them
                public int additional_strikes = 0;
                /// Delay between additional strikes, actual value: multiplied by duration, rounded to whole ticks.
                public float additional_strike_delay = 0.25F;
                /// If true additional hits on the same target are allowed.
                public boolean additional_hits_on_same_target = true;

                /// Forward momentum applied to the caster when performing this melee attack
                public float forward_momentum = 0F;
                /// Whether forward momentum can be applied while airborne
                public boolean allow_momentum_airborne = false;
                /// Multiplier applied to the movement speed while executing this melee attack.
                public float movement_speed = 1F;
                /// Bonus applied to block slipperiness. Use positive value to slide further.
                /// grass is 0.6, ice is 0.98
                public float movement_slipperiness = 0F;
                /// Collision detection shape of this attack.
                public HitBox hitbox = new HitBox();

                public PlayerAnimation animation;
                /// The sound to be played when the melee attack is performed.
                public Sound swing_sound;
                /// The sound to be played when the melee attack hits a target.
                public Sound impact_sound;
                /// The maximum number of times the impact sound to be played, to avoid overwhelming the audio channel when hitting lots of targets.
                /// Zero means no limit.
                public int impact_sound_cap = 3;
                public ParticleBatch[] particles = new ParticleBatch[]{};
            }
            public static class HitBox {
                /// Relative length of the hitbox, will be scaled up by attack range.
                public float length = 1F;
                /// Relative width of the hitbox, will be scaled up by attack range.
                public float width = 1F;
                /// Relative height of the hitbox, will be scaled up by attack range.
                public float height = 1F;
                /// Rotation along the forward axis, in degrees.
                /// Positive values rotate clockwise, negative values rotate counterclockwise.
                public float roll = 0F;
                /// Arc of the melee attack hitbox, in degrees. 0 means no angular checks.
                public float arc = 0F;
            }
        }

        public List<Cloud> clouds;
        public static class Cloud { public Cloud() { }
            // Custom entity type id to spawn, must be a subclass of `SpellCloud`
            @Nullable public String entity_type_id;
            public AreaImpact volume = new AreaImpact();
            public float time_to_live_seconds = 0;

            /// The number of ticks between looking for targets and trying to apply impact
            public int impact_tick_interval = 5;
            /// The number of times impacts can be performed, zero means unlimited
            public int impact_cap = 0;
            public ParticleBatch[] impact_particles = new ParticleBatch[]{};

            /// Base spawn delay
            public int delay_ticks = 0;
            public boolean placement_delay_stacks = true;
            public EntityPlacement placement = new EntityPlacement();
            public List<EntityPlacement> additional_placements = List.of();

            @Nullable public Sound presence_sound;
            public Cloud.ClientData client_data = new Cloud.ClientData();
            public static class ClientData {
                public int light_level = 0;
                public ParticleBatch[] particles = new ParticleBatch[]{};
                public int particle_spawn_interval = 1;
                /// Particles to be spawned at the interval of `particle_spawn_interval`
                /// Useful for ground particles with fixed animation duration
                public ParticleBatch[] interval_particles = new ParticleBatch[]{};
                public ProjectileModel model;
            }
            public Cloud.Spawn spawn = new Cloud.Spawn();
            public static class Spawn {
                public Sound sound;
                public ParticleBatch[] particles = new ParticleBatch[]{};
            }
        }

        public StashEffect stash_effect;
        public static class StashEffect {
            /// Spells with valid `stash_effect` get automatically linked
            /// to the status effect specified below.
            /// No java code required.

            /// ID of the status effect, that will stash this spell.
            public String id;
            /// Stacks to apply -1
            public int amplifier = 0;
            public float amplifier_power_multiplier = 0;
            /// Whether effect stacks should be added one by one, or all at once
            public boolean stacking = false;
            /// Duration of the status effect in seconds
            public float duration = 10;
            public boolean show_particles = false;

            /// Trigger of the status effect
            public List<Trigger> triggers = List.of();
            /// Status effect stacks to consume upon triggering
            public int consume = 1;
            /// Whether the stash effect should be consumed next tick
            public boolean consumed_next_tick = false;
            /// Whether the stash effect should consume the stack available, or wait for larger than `consume` stacks
            public boolean consume_any_stacks = false;
            /// Determines what happens to the impacts of the spell when using this stash
            public ImpactMode impact_mode = ImpactMode.PERFORM;
            public enum ImpactMode {
                PERFORM,    /// Perform the impacts, on the target that is available at the time of triggering
                TRANSFER    /// Pass the impacts onto a projectile, that will be launched at the time of triggering
            }
        }

        public Custom custom;
        public static class Custom { public Custom() { }
            /// ID of the handler
            public String handler;
        }
    }

    public List<Impact> impacts = List.of();
    public static class Impact { public Impact() { }
        /// The chance to perform this impact
        public float chance = 1F;
        /// Magic school of this specific impact, if null then spell school is used
        @Nullable public SpellSchool school;
        public boolean attribute_from_target = false;
        /// Attribute the value of which to override the power
        @Nullable public String attribute;
        public List<TargetModifier> target_modifiers = List.of();
        public static class TargetModifier {
            // If true = AND, if false = OR
            public boolean all_required = false;
            public List<TargetCondition> conditions = List.of();

            /// Decides whether this impact should be carried out
            /// - ALLOW: Executes the impact if conditions are met
            /// - PASS: Executes the impact regardless of conditions
            /// - DENY: Executes the impact if conditions are NOT met
            public TriState execute = TriState.PASS;
            /// Applies power modifiers for this impact (if executed)
            @Nullable public Modifier modifier;
        }
        public static class Modifier {
            // Combined as `ADD_MULTIPLIED_BASE` in `EntityAttributeModifier.Operation`
            public float power_multiplier = 0;
            // Combined as `ADD_VALUE` in `EntityAttributeModifier.Operation`
            public float critical_chance_bonus = 0;
            // Combined as `ADD_VALUE` in `EntityAttributeModifier.Operation`
            public float critical_damage_bonus = 0;
        }

        public Action action;
        public static class Action { public Action() { }
            public Type type;
            /// Whether as an area impact, should be executed on the center target
            public boolean allow_on_center_target = true;
            public boolean apply_to_caster = false;
            public float min_power = 1;
            public float max_power = 999999;
            public enum Type {
                DAMAGE,
                HEAL,
                STATUS_EFFECT,
                FIRE,
                SPAWN,
                TELEPORT,
                COOLDOWN,
                AGGRO,
                DISRUPT,
                IMMUNITY,
                CUSTOM
            }
            public Damage damage;
            public static class Damage { public Damage() { }
                public boolean bypass_iframes = true;
                public float spell_power_coefficient = 1;
                public float knockback = 1;
            }
            public Heal heal;
            public static class Heal { public Heal() { }
                public float spell_power_coefficient = 1;
            }
            public StatusEffect status_effect;
            public static class StatusEffect { public StatusEffect() { }
                /// ID of the status effect to apply
                public String effect_id;
                /// Duration of the status effect in seconds
                public float duration = 10;
                /// How many stacks to apply (0 = 1, 1 = 2, 2 = 3, etc...)
                public int amplifier = 0;
                /// How many additional stacks to apply based on power
                public float amplifier_power_multiplier = 0;
                /// Maximum stacks to apply (ignored by mode `ADD`, where this is achieved by `amplifier`)
                public int amplifier_cap = 0;
                /// Maximum additional stacks to apply based on power
                public float amplifier_cap_power_multiplier = 0;
                /// Whether already applied stacks should be refreshed
                public boolean refresh_duration = true;

                public enum ApplyMode { SET, ADD, REMOVE }
                public ApplyMode apply_mode = ApplyMode.SET;

                @Nullable public ApplyLimit apply_limit;
                public static class ApplyLimit { public ApplyLimit() { }
                    public float health_base = 0;
                    public float spell_power_multiplier = 0;
                }
                public boolean show_particles = false;

                public Remove remove;
                public static class Remove { public Remove() { }
                    public enum Selector { RANDOM, FIRST }
                    /// Status effect id pattern
                    /// (Universal pattern matcher)
                    @Nullable public String id;
                    public Selector selector = Selector.RANDOM;
                    public boolean select_beneficial = false;
                }
            }

            public Fire fire;
            public static class Fire { public Fire() { }
                /// Number of seconds the target is on fire
                public float duration = 2;
                // Entity.java - Notice `% 20` - tick offset is used to avoid instant hits
                // if (this.fireTicks % 20 == 0 && !this.isInLava()) {
                //    this.damage(DamageSource.ON_FIRE, 1.0f);
                // }
                public int tick_offset = 10;
            }

            // Populate either `spawn` or `spawns` but not both
            public List<Spawn> spawns;
            public static class Spawn { public Spawn() { }
                public SpellTarget.Intent intent = SpellTarget.Intent.HELPFUL;
                // Custom entity type id to spawn
                // Implement `SpellEntity.Spawned` to receive information about spawning context
                public String entity_type_id;
                public int time_to_live_seconds = 0;
                public int delay_ticks = 0;
                public EntityPlacement placement = new EntityPlacement();

                public Spawn copy() {
                    Spawn copy = new Spawn();
                    copy.intent = this.intent;
                    copy.entity_type_id = this.entity_type_id;
                    copy.time_to_live_seconds = this.time_to_live_seconds;
                    copy.delay_ticks = this.delay_ticks;
                    copy.placement = this.placement;
                    return copy;
                }
            }

            public Teleport teleport;
            public static class Teleport { public Teleport() { }
                public enum Mode { FORWARD, BEHIND_TARGET }
                public Mode mode;
                public int required_clearance_block_y = 1;
                public SpellTarget.Intent intent = SpellTarget.Intent.HELPFUL;
                public Forward forward;
                public static class Forward { public Forward() { }
                    public float distance = 10;
                }
                public BehindTarget behind_target;
                public static class BehindTarget { public BehindTarget() { }
                    public float distance = 1.5F;
                }
                @Nullable public ParticleBatch[] depart_particles;
                @Nullable public ParticleBatch[] arrive_particles;
            }

            public Cooldown cooldown;
            public static class Cooldown { public Cooldown() { }
                @Nullable public Modify actives;
                @Nullable public Modify passives;
                public static class Modify { public Modify() { }
                    /// Spell school regex
                    @Nullable public String school;
                    /// ID of the spell
                    /// (Universal pattern matcher: `#` prefix checks tag, `~` prefix checks regex, no prefix checks exact match)
                    @Nullable public String id;
                    public float duration_add = 0;
                    public float duration_multiplier = 1;
                }
            }

            public Aggro aggro;
            public static class Aggro { public Aggro() { }
                /// Executes the aggro change only of the caster is targeted by the target
                public boolean only_if_targeted = false;
                /// What to do with the aggro
                public enum Mode {
                    /// Taunt the target, so it will attack the caster
                    SET,
                    /// Clear the taunt from the target, so it will not attack the caster
                    CLEAR
                }
                public Mode mode = Mode.SET;
            }

            public Disrupt disrupt;
            public static class Disrupt {  public Disrupt() { }
                public boolean shield_blocking = false;
                public float item_usage_seconds = 0F;
            }

            public Immunity immunity;
            public static class Immunity { public Immunity() { }
                /// Damage type specifier, id or tag, for example:
                /// - `#minecraft:bypasses_armor` - tag
                /// - `minecraft:drown` - id
                /// - null means all damage types
                public @Nullable String damage_type;
                /// Whether the DamageSource needs to be damageIndirect or not, for example:
                /// - player melee attacks are direct
                /// - spell projectile impacts (such as Fireball) are direct
                /// - spell area effects (such as Fire Breath) are damageIndirect
                public @Nullable Boolean damage_indirect;
                /// Whether the immunity should block harmful effects in general
                public boolean effect_any_harmful = false;
                /// Duration of the invulnerability in ticks
                public int duration_ticks = 20;
            }

            public Custom custom;
            public static class Custom { public Custom() { }
                public SpellTarget.Intent intent = SpellTarget.Intent.HELPFUL;
                /// ID of the handler
                public String handler;
            }
        }

        public ParticleBatch[] particles = new ParticleBatch[]{};
        public Sound sound;
    }
    /// Apply this impact to other entities nearby
    @Nullable public AreaImpact area_impact;

    @Nullable public ArrowPerks arrow_perks = null;
    public static class ArrowPerks { public ArrowPerks() { }
        public float damage_multiplier = 1F;
        public float velocity_multiplier = 1F;
        public boolean bypass_iframes = false;
        public int iframe_to_set = 0;
        public boolean skip_arrow_damage = false;
        public int pierce = 0;
        public float knockback = 1;
        public ParticleBatch[] travel_particles = new ParticleBatch[]{};
        public ParticleBatch[] launch_particles = new ParticleBatch[]{};
        @Nullable public Sound launch_sound;
        @Nullable public ProjectileModel override_render;
    }

    /// Applied to the caster, once the spell casting process finishes
    public Cost cost = new Cost();
    public static class Cost { public Cost() { }
        /// Whether the cost should be executed at the end of the game tick
        /// So multiple targets can be affected by a triggered execution
        public boolean batching = false;
        /// Exhaust to add
        public float exhaust = 0.1F;
        /// Durability of the spell host item to consume
        public int durability = 1;
        /// Status effect to remove
        /// (Useful for channeled spells)
        @Nullable public String effect_id;

        public Cooldown cooldown = new Cooldown();
        public static class Cooldown {
            /// Arbitrary group code, used to share cooldowns between multiple spells
            @Nullable public String group;
            /// Duration of the cooldown applied on spell cast attempt in seconds (useful for delayed deliveries)
            public float attempt_duration = 0;
            /// Duration of the cooldown in seconds
            public float duration = 0;
            /// Whether the duration to be multiplied by channeling duration
            public boolean proportional = false;
            /// Whether the cooldown is affected by haste
            public boolean haste_affected = true;
            /// Whether item cooldown is imposed onto the hosting item of this spell
            public boolean hosting_item = true;
        }

        @Nullable public Item item;
        public static class Item {
            /// ID or Tag
            /// (When using tags, make sure to have a translation for tha tag)
            public String id;
            /// How many of the item is consumed
            public int amount = 1;
            /// When set to false, spell cast attempt will check availability,
            /// but upon successful cast will not be consumed
            /// (Useful for archery skills)
            public boolean consume = true;
        }
    }

    // MARK: Shared structures (used from multiple places in the spell structure)

    public static class Trigger {
        @Nullable public TargetSelector target_override;
        @Nullable public TargetSelector aoe_source_override;
        public enum TargetSelector { CASTER, AOE_SOURCE, TARGET }

        public enum Type {
            ARROW_SHOT, ARROW_IMPACT,
            MELEE_IMPACT,
            SPELL_CAST, SPELL_IMPACT_ANY, SPELL_IMPACT_SPECIFIC,
            SPELL_AREA_IMPACT,
            EFFECT_TICK, /// Only works for specifically coded Status Effect implementations
            EVASION, /// Performed when the caster evades an attack
            DAMAGE_TAKEN, SHIELD_BLOCK,
            ROLL  /// Only works when Combat Roll mod is installed
        }
        public Type type;
        public enum Stage { PRE, POST }
        /// Represents when the trigger happens
        /// - PRE: Before the actual event
        /// - POST: After the actual event
        /// Only works for some trigger types
        public Stage stage = Stage.POST;
        /// Number of ticks to wait before the spell is performed after a successful trigger
        public int fire_delay = 0;
        /// Limits the number of times this trigger can be executed per game tick
        /// (0 = unlimited)
        public int cap_per_tick = 0;
        /// Chance to trigger. 0 = 0%, 1 = 100%
        public float chance = 1;
        /// Calculates and stores the chance, for the duration of a single game tick
        /// So multiple targets can be affected by the same chance
        public boolean chance_batching = false;
        /// When a value is given, this spell need to be present on the equipped item of the given slot
        /// Recommended use: EquipmentSlot.MAINHAND for weapon passives, so attacks with inactive hand don't trigger
        @Nullable public EquipmentSlot equipment_condition;

        @Nullable public List<TargetCondition> caster_conditions;
        @Nullable public List<TargetCondition> target_conditions;

        /// Evaluated for: SPELL_CAST, SPELL_IMPACT_ANY, SPELL_IMPACT_SPECIFIC
        public SpellCondition spell;
        public static class SpellCondition { public SpellCondition() { }
            /// Spell school regex
            @Nullable public String school;
            /// Exact archetype of the spell school
            @Nullable public SpellSchool.Archetype archetype;
            /// Exact type of the spell
            @Nullable public Spell.Type type;
            /// ID of the spell
            /// (Universal pattern matcher: `#` prefix checks tag, `~` prefix checks regex, no prefix checks exact match)
            @Nullable public String id;
            /// The spell needs to have at least his long of a cooldown
            public float cooldown_min = 0;
            // Maybe add predicate, that can be registered in java, and resolved by this id
            // public String spell_predicate
        }
        /// Evaluated for: SPELL_IMPACT_SPECIFIC
        public ImpactCondition impact;
        public static class ImpactCondition { public ImpactCondition() { }
            // Impact type regex
            @Nullable public String impact_type;
            @Nullable public Boolean critical;
        }

        public DamageCondition damage;
        public static class DamageCondition { public DamageCondition() { }
            /// Minimum damage
            @Nullable public Float amount_min = null;
            /// Maximum damage
            @Nullable public Float amount_max = null;
            /// Damage type pattern
            @Nullable public String damage_type;
            /// Whether the damage amount is greater than the amount of health the target has
            @Nullable public Boolean fatal;
        }
        /// Evaluated for: MELEE_IMPACT
        public MeleeCondition melee;
        public static class MeleeCondition { public MeleeCondition() { }
            @Nullable public Boolean is_combo;
            @Nullable public Boolean is_offhand;
        }
        /// Evaluated for: EFFECT_TICK
        public EffectCondition effect;
        public static class EffectCondition { public EffectCondition() { }
            /// ID of the status effect
            public String id;
        }
        /// Evaluated for: ARROW_SHOT
        public ArrowShotCondition arrow_shot;
        public static class ArrowShotCondition { public ArrowShotCondition() { }
            /// Defines the source of the arrow shot
            /// `true`  - from a spell
            /// `false` - from a bow or crossbow
            @Nullable public Boolean from_spell;
        }
    }

    public static class AreaImpact { public AreaImpact() { }
        /// Only impacts of this type to trigger area impact
        /// If null, all impacts trigger area impact
        @Nullable public Impact.Action.Type triggering_action_type;
        /// Only impacts of this type to execute upon area impact
        /// If null, all impacts execute upon area impact
        @Nullable public Impact.Action.Type execute_action_type;

        public boolean force_indirect = false;

        public float radius = 1F;
        public ExtraRadius extra_radius = new ExtraRadius();
        public static class ExtraRadius {
            public float power_coefficient = 0;
            public float power_cap = 0;
        }
        public Target.Area area = new Target.Area();
        public ParticleBatch[] particles = new ParticleBatch[]{};
        @Nullable
        public Sound sound;

        public float combinedRadius(double power) {
            return radius + extra_radius.power_coefficient * (float) Math.min(extra_radius.power_cap, power);
        }
    }

    public static class LaunchProperties { public LaunchProperties() { }
        /// Initial velocity of the projectile
        public float velocity = 1F;
        /// When channeling, the index of the channel to, when extra projectiles to shoot
        public int extra_launch_mod = -1;
        /// How many additional projectiles are spawned after launch
        public int extra_launch_count = 0;
        /// How many ticks after launch additional projectiles are spawned
        public int extra_launch_delay = 2;
        /// The sound to play on launch
        @Nullable public Sound sound;

        public LaunchProperties velocity(float value) {
            this.velocity = value;
            return this;
        }
        public LaunchProperties copy() {
            LaunchProperties copy = new LaunchProperties();
            copy.velocity = this.velocity;
            copy.extra_launch_mod = this.extra_launch_mod;
            copy.extra_launch_count = this.extra_launch_count;
            copy.extra_launch_delay = this.extra_launch_delay;
            copy.sound = this.sound != null ? this.sound.copy() : null;
            return copy;
        }

        public void mutatingCombine(LaunchProperties other)  {
            this.velocity += other.velocity;
            this.extra_launch_mod = other.extra_launch_mod >= 0 ? other.extra_launch_mod : this.extra_launch_mod;
            this.extra_launch_count += other.extra_launch_count;
            this.extra_launch_delay += other.extra_launch_delay;
        }

        public static LaunchProperties EMPTY() {
            LaunchProperties empty = new LaunchProperties();
            empty.velocity = 0;
            empty.extra_launch_mod = -1;
            empty.extra_launch_count = 0;
            empty.extra_launch_delay = 0;
            return empty;
        }
    }

    public static class ProjectileData { public ProjectileData() { }
        public float divergence = 0;
        public float homing_angle = 1F;
        @Nullable public float[] homing_angles = null;
        public float homing_after_absolute_distance = 0;
        public float homing_after_relative_distance = 0;
        /// The frequency of playing the travel sound in ticks
        public int travel_sound_interval = 20;
        @Nullable public Sound travel_sound;

        public Perks perks = new Perks();
        public static class Perks { public Perks() { }
            /// How many entities projectile can ricochet to
            public int ricochet = 0;
            /// How far ricochet can look for a target
            public float ricochet_range = 5;
            /// How many times projectile can bounce off a wall
            public int bounce = 0;
            /// Whether ricochet and bounce should be decremented together
            public boolean bounce_ricochet_sync = true;
            /// How many entities projectile can go through
            public int pierce = 0;
            /// How many additional projectiles are spawned on impact
            public int chain_reaction_size = 0;
            /// How many generation of chain reaction projectiles are spawned
            public int chain_reaction_triggers = 1;
            /// How many more projectiles are spawned from chain reaction of a spawned projectile
            public int chain_reaction_increment = -1;

            public Perks copy() {
                Perks copy = new Perks();
                copy.ricochet = this.ricochet;
                copy.ricochet_range = this.ricochet_range;
                copy.bounce = this.bounce;
                copy.bounce_ricochet_sync = this.bounce_ricochet_sync;
                copy.pierce = this.pierce;
                copy.chain_reaction_size = this.chain_reaction_size;
                copy.chain_reaction_triggers = this.chain_reaction_triggers;
                copy.chain_reaction_increment = this.chain_reaction_increment;
                return copy;
            }

            public void mutatingCombine(Perks other) {
                this.ricochet += other.ricochet;
                this.ricochet_range += other.ricochet_range;
                this.bounce += other.bounce;
                this.pierce += other.pierce;
                this.chain_reaction_size += other.chain_reaction_size;
                this.chain_reaction_triggers += other.chain_reaction_triggers;
                this.chain_reaction_increment += other.chain_reaction_increment;
            }

            public static Perks EMPTY() {
                Perks empty = new Perks();
                empty.ricochet = 0;
                empty.ricochet_range = 0;
                empty.bounce = 0;
                empty.pierce = 0;
                empty.chain_reaction_size = 0;
                empty.chain_reaction_triggers = 0;
                empty.chain_reaction_increment = 0;
                return empty;
            }
        }

        /// Custom bounding box of the projectile.
        /// When set, enables volumetric (OBB/SAT) collision detection instead of raycast.
        @Nullable public HitBox hitbox;
        public static class HitBox { public HitBox() { }
            public HitBox(float width, float height) { this.width = width; this.height = height; }
            public float width = 0.5F;
            public float height = 0.5F;
            /// OBB depth along the travel direction; 0 = use width as fallback
            public float length = 0;
        }

        public Client client_data;
        public static class Client { public Client() { }
            /// Ambient light level of the projectile, like players holding torches
            /// Requires `LambDynamicLights` to be installed
            /// Example values:
            /// 14 - torch
            /// 10 - soul torch
            public int light_level = 0;
            public ParticleBatch[] travel_particles = new ParticleBatch[]{};
            public ProjectileModel model;
        }
    }

    public static class ProjectileModel { public ProjectileModel() { }
        public boolean use_held_item = false;
        public String model_id;
        public LightEmission light_emission = LightEmission.GLOW;
        public float scale = 1F;
        public float rotate_degrees_per_tick = 2F;
        public float rotate_degrees_offset = 0;
        public Orientation orientation = Orientation.TOWARDS_MOTION;
        public enum Orientation {
            TOWARDS_CAMERA, TOWARDS_MOTION, ALONG_MOTION
        }
    }

    public static class EntityPlacement { public EntityPlacement() { }
        // If greater than 0, the entity will be placed at the caster's look direction, by this many blocks
        public int delay_ticks = 0;
        public boolean force_onto_ground = true;
        public float location_offset_by_look = 0;
        public float location_yaw_offset = 0;
        public boolean apply_yaw = false;
        public boolean apply_pitch = false;
        public float location_offset_x = 0;
        public float location_offset_y = 0;
        public float location_offset_z = 0;
    }

    public static class TargetCondition {
        public float health_percent_above = 0;
        public float health_percent_below = 1;
        /// ID of the entity type
        /// (Universal pattern matcher: `#` prefix checks tag, `~` prefix checks regex, no prefix checks exact match)
        @Nullable public String entity_type;
        /// ID of a registered SpellEntityPredicate
        /// Check out `SpellEntityPredicates` class for options, or new registration
        @Nullable public String entity_predicate_id;
        /// Parameter to pass to the predicate
        /// Each predicate handles this differently
        @Nullable public String entity_predicate_param;
    }
}