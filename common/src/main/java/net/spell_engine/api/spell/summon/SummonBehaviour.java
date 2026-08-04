package net.spell_engine.api.spell.summon;

import com.google.gson.Gson;
import net.spell_engine.api.spell.fx.ParticleGroup;
import net.spell_engine.api.spell.fx.Sound;
import net.spell_engine.api.spell.fx.Fx;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SummonBehaviour {

    public boolean is_attackable = true;

    /// Gson used for the deep copy below. A plain `Gson` round-trips a `SummonBehaviour` losslessly —
    /// it is the very same mechanism `SummonedEntity` uses to persist the behaviour to/from NBT, so
    /// every field (including nested structs and lists) is known to serialize cleanly. Any remaining
    /// `transient` `Supplier` field (e.g. on `Action.MeleeAttack`) is intentionally skipped and
    /// lazily re-created on the copy.
    private static final Gson GSON = new Gson();

    /// Returns a fully independent deep copy of this behaviour, sharing no mutable sub-structure with
    /// the original. The same {@link SummonBehaviour} instance is shared across every summon of a
    /// registered spell, so it must never be mutated in place — callers that need to tweak it (e.g.
    /// applying spell modifiers) must work on a copy.
    public SummonBehaviour deepCopy() {
        return GSON.fromJson(GSON.toJson(this), SummonBehaviour.class);
    }

    /// Returns a deep copy of this behaviour with summon spell modifiers applied, leaving this
    /// instance untouched. `extraActions` are appended after the behaviour's own actions; the lifespan
    /// adds are applied per phase and floored at zero. The copy is independent, so its sub-structures
    /// are mutated directly.
    public SummonBehaviour withModifiers(@Nullable List<Action.Entry> extraActions,
                                         int spawnTicksAdd, int activeSecondsAdd, int despawnTicksAdd) {
        var copy = deepCopy();
        if (extraActions != null && !extraActions.isEmpty()) {
            copy.actions.addAll(extraActions);
        }
        if (spawnTicksAdd != 0 || activeSecondsAdd != 0 || despawnTicksAdd != 0) {
            copy.lifespan.spawn_ticks    = Math.max(0, copy.lifespan.spawn_ticks    + spawnTicksAdd);
            copy.lifespan.active_seconds = Math.max(0, copy.lifespan.active_seconds + activeSecondsAdd);
            copy.lifespan.despawn_ticks  = Math.max(0, copy.lifespan.despawn_ticks  + despawnTicksAdd);
        }
        return copy;
    }

    // --- Spawn FX ---

    /// One-shot FX emitted server-side when this individual summon enters the world (on its first
    /// tick, alongside the spawn sound). Null = none.
    @Nullable public Fx.Visuals spawn_fx = null;

    /// One-shot FX emitted server-side when this individual summon enters its despawn phase
    /// (alongside the despawn sound). Null = none.
    @Nullable public Fx.Visuals despawn_fx = null;

    // --- Existence FX ---

    /// Particle effects emitted on a tick interval for the duration of the summon's ACTIVE phase.
    /// These are spawned CLIENT-SIDE: the config is synced once (via a DataTracker) and the client
    /// plays the particles locally each interval, so they generate no per-tick network traffic —
    /// mirroring SpellCloud's `client_data` particles.
    public List<ExistenceParticles> existence_particles = List.of();
    public static class ExistenceParticles {
        public List<ParticleGroup> particles = List.of();
        /// Emit every N ticks of entity age (1 = every tick). Values <= 0 disable this entry.
        public int interval_ticks = 20;
        /// Tick offset within the interval, so multiple entries can be phase-shifted.
        public int offset_ticks = 0;
    }

    // --- Dimensions ---

    /// Optional override for the entity's bounding-box size. `null` (the default) means
    /// "inherit from the EntityType" — i.e. use whatever value was passed to
    /// `FabricEntityTypeBuilder.dimensions(...)` at registration. Assign a non-null
    /// `Dimensions` to override per-summon.
    @Nullable public Dimensions dimensions = null;
    public static class Dimensions {
        public float width  = 0.6F;
        public float height = 1.8F;
    }

    // --- Movement ---

    public Movement movement = new Movement();
    public static class Movement {
        /// Stationary if false
        public boolean can_move = true;
        public boolean affected_by_gravity = true;
        /// Whether this summon takes suffocation damage while embedded in blocks. False (default)
        /// makes it immune: a summon is positioned by the spell rather than by the player, so it can
        /// end up inside terrain through no fault of its own — and a temporary pet ticking down to
        /// death inside a wall is frustration with no gameplay upside. Set true only for summons
        /// where being walled in should be a real risk.
        public boolean suffocates = false;

        public Wander wander = new Wander();
        public static class Wander {
            public double speed = 1.0;
            /// Chance per tick to start wandering (0.0–1.0).
            public float probability = 0.001F;
        }

        public Follow follow;
        public static class Follow {
            /// Distance at which the entity begins moving toward the owner.
            public float start_distance = 10F;
            /// Distance at which the entity stops moving toward the owner.
            public float stop_distance = 4.0f;
            /// If greater than 0, teleports to the owner when the distance exceeds this value.
            public float teleport_after_distance = 12F;
        }

        public boolean is_pushable = true;
        public CollisionMode collision = CollisionMode.ENEMIES;
        public enum CollisionMode {
            NONE, ALL, ENEMIES
        }
    }

    // --- Targeting ---

    public Targeting targeting = new Targeting();
    public static class Targeting {
        public boolean revenge = true;
        public boolean attack_with_owner = true;
        /// Number of consecutive hits the owner has to do on the same entity,
        /// in order to make this summon target that entity
        public int attack_with_owner_hits = 1; // TODO `retarget_hits`
        /// What kind of target the entity auto-acquires when nothing else is set.
        ///   NONE     — no auto-targeting; relies on revenge / attack_with_owner only
        ///   HOSTILE  — acquires nearby hostile mobs (attacker pets)
        ///   FRIENDLY — acquires nearby wounded allies, including the owner (healer pets)
        ///   BOTH     — installs both goals; friendly takes priority (heal first, then fight)
        public AutoTarget automatic_targeting = AutoTarget.NONE;
        public boolean look_around = true;
        /// How far `automatic_targeting` reaches when acquiring (and keeping) a target.
        /// Independent of follow range so a summon's detection radius can be tuned without
        /// touching its other follow-range-driven AI.
        public DetectionRange detection_range = new DetectionRange();
        public static class DetectionRange {
            /// How the detection radius is determined:
            ///   FOLLOW_RANGE         — the GENERIC_FOLLOW_RANGE attribute (vanilla default).
            ///   MAXIMUM_ACTION_RANGE — the largest effective range across the summon's
            ///                          actions (spell ranges + melee reach), so it only
            ///                          detects what it can actually act on. Falls back to
            ///                          FOLLOW_RANGE when no action yields a positive range.
            ///   STATIC               — a fixed `value` in blocks.
            public Mode mode = Mode.FOLLOW_RANGE;
            public enum Mode { FOLLOW_RANGE, MAXIMUM_ACTION_RANGE, STATIC }
            /// Detection radius in blocks. Used only when mode == STATIC.
            public float value = 16F;
        }

        public enum AutoTarget { NONE, HOSTILE, FRIENDLY, BOTH }

        /// Optional target-clear triggers. Null (default) preserves the prior behaviour —
        /// the summon never drops a target on its own. Each sub-block fires independently,
        /// nulling the current target when its trigger condition is met.
        @Nullable public ClearCondition clear_condition = null;

        public static class ClearCondition {
            /// Action-completion triggers, evaluated when a goal reports an action just
            /// finished (a melee swing reached full duration; a spell cast reached release).
            /// Checked in order: the first entry whose filter matches rolls its own `chance`
            /// and then evaluation stops — so a `chance=0` entry acts as an exclusion placed
            /// ahead of broader entries. Empty (default) = no action-completion clearing.
            public List<OnActionCompleted> on_action_completed = List.of();
            /// Fires each tick once the target has been held long enough. Null = disabled.
            @Nullable public AfterTicks after_ticks = null;
            /// Fires each tick the target is too far away. Null = disabled.
            @Nullable public OutOfDetectionRange out_of_detection_range = null;

            /// One action-completion trigger: a `chance` to drop the target when a matching
            /// action completes, narrowed by action type and (for spells) spell id.
            public static class OnActionCompleted {
                /// Probability in `[0..1]` rolled when this entry matches. 1 always clears;
                /// 0 never clears but still stops evaluation (acts as an exclusion).
                public float chance = 1F;
                /// Action type to match. `null` = any type.
                @Nullable public Action.Type action_type = null;
                /// Spell id to match. `null` = any spell. Ignored when the completed action
                /// is not a `SPELL_CAST`.
                @Nullable public String spell_id = null;
            }

            /// Trigger that fires every tick once the entity has held its current
            /// target for at least `ticks` ticks. Acquisition is reset whenever
            /// `setTarget` switches to a different non-null target.
            public static class AfterTicks {
                public int ticks = 0;
            }

            /// Trigger that fires every tick the current target is farther than
            /// `multiplier × detection range` from the summon. Use to drop targets that
            /// have fled well beyond what the summon can act on. The detection range is the
            /// same value `automatic_targeting` uses (see `Targeting.detection_range`).
            public static class OutOfDetectionRange {
                /// Distance threshold as a multiple of the detection range. E.g. 2 clears
                /// the target once it is more than twice the detection range away.
                public float multiplier = 1.5F;
            }
        }
    }

    // --- Lifespan ---

    public Lifespan lifespan = new Lifespan();
    public static class Lifespan {
        /** Ticks the entity spends in the spawning phase (inactionable). */
        public int spawn_ticks = 10;
        /** Seconds the entity spends in the active phase. Total entity lifespan in ticks is
         *  `spawn_ticks + active_seconds * 20 + despawn_ticks`. */
        public int active_seconds = 60;
        /** Ticks the entity spends in the despawning phase (inactionable) before being discarded. */
        public int despawn_ticks = 10;
    }

    // --- Sounds ---

    public Sounds sounds = new Sounds();
    public static class Sounds {
        /// Played once when the entity is summoned. Null for none.
        @Nullable public Sound spawn = null;
        /// Played once when the entity enters its despawn phase. Null for none.
        @Nullable public Sound despawn = null;
        /// Backs `getHurtSound`. Null falls back to the vanilla generic hurt sound.
        @Nullable public Sound hurt = null;
        /// Backs `getDeathSound`. Null falls back to the vanilla generic death sound.
        @Nullable public Sound death = null;
        /// Backs `getAmbientSound` — vanilla's periodic mob-idle sound. Null disables
        /// it (vanilla MobEntity default is also null, so no ambient noise plays).
        @Nullable public Sound ambient = null;
        /// Played on each footstep from `playStepSound`. Null falls back to the block's
        /// step sound (vanilla behaviour: stone-step on stone, wood-step on planks, etc.).
        @Nullable public Sound step = null;

        // Each entry is an fx.Sound (id + volume + pitch + randomness). SoundEvent resolution and
        // playback (honoring volume/pitch) live on SummonedEntity, not here — a Gson-deserialized
        // behaviour cannot be relied upon to carry live `transient` fields (allocation paths that
        // skip field initializers leave them null). See SummonedEntity's sound suppliers.
    }

    // --- Actions ---

    public List<Action.Entry> actions = List.of();
    public static class Action {
        public enum Type {
            MELEE_ATTACK, SPELL_CAST
        }
        public static class Entry {
            public Type type;
            public MeleeAttack melee_attack;
            public SpellCast spell_cast;
        }

        public static Entry attack(float max_range, float speed) {
            var a = new MeleeAttack();
            a.max_range = max_range;
            a.speed = speed;
            var e = new Entry();
            e.type = Type.MELEE_ATTACK;
            e.melee_attack = a;
            return e;
        }
        public static Entry attack(MeleeAttack melee_attack) {
            var e = new Entry();
            e.type = Type.MELEE_ATTACK;
            e.melee_attack = melee_attack;
            return e;
        }
        public static class MeleeAttack {
            /// If greater than 0, swing is only initiated while the target is within this range.
            public float max_range = 3;
            /// Coefficient controlling how much the entity's scale extends `max_range`.
            /// Effective max_range = `max_range * (1 + entityScale * attack_range_scaling)`.
            public float attack_range_scaling = 0.5F;
            /// Attack speed, in attacks per second. The cooldown between swing starts is
            /// max(duration, 20 / speed) ticks.
            public float speed = 1.2F;
            /// Pathfinding speed multiplier used while approaching the target.
            public float movement_speed = 1.0F;
            /// Total length of one swing, in ticks.
            public int duration = 20;
            /// Fraction of `duration` after which the impact lands (0 = on swing start,
            /// 0.5 = midway, 1 = on the final tick). The impact tick is `round(duration * windup)`.
            public float windup = 0.5F;
            /// Multiplier applied to `movement_speed` while the swing is in progress (0 = stop).
            public float movement_modifier = 0.5F;
            /// Radius around the primary target in which additional entities are also struck.
            /// 0 = single-target.
            public float radius = 0;
            /// Sound played at swing start (e.g. `Sound.of(Identifier.of("minecraft:entity.player.attack.sweep"))`).
            /// Null disables it. Its volume / pitch / randomness are honored on playback.
            @Nullable public Sound swing_sound = null;
            /// Sound played once on a successful impact (when the swing reaches its windup
            /// tick with the target still in range). AoE radius hits do not retrigger it.
            /// Null disables it. Its volume / pitch / randomness are honored on playback.
            @Nullable public Sound impact_sound = null;
            /// Pool of animation variant numbers to choose from on each swing. One is picked
            /// uniformly at random and synced to the client via the entity's variant tracker;
            /// the model picks the matching animation (falling back to variant 1 when its
            /// own animation set doesn't have a match).
            public List<Integer> animation_variants = List.of(1);
        }

        public static Entry spell(SpellCast spell_cast) {
            var e = new Entry();
            e.type = Type.SPELL_CAST;
            e.spell_cast = spell_cast;
            return e;
        }
        public static Entry spell(String spell_id, int cooldown) {
            var s = new SpellCast();
            s.spell_id = spell_id;
            s.cooldown = cooldown;
            var e = new Entry();
            e.type = Type.SPELL_CAST;
            e.spell_cast = s;
            return e;
        }
        public static class SpellCast {
            public String spell_id = "";
            public int cooldown = 20;
            /// Controls what the action fires at: whether it uses an acquired target, and
            /// where it aims when it has none. Default reproduces the original targeted-only
            /// behaviour (use the target; don't fire without one).
            public Aiming aiming = new Aiming();
            public static class Aiming {
                /// When true, fire at the entity's currently acquired target (from revenge,
                /// defend-owner, mirror-owner or auto-targeting) whenever one sits inside the
                /// engagement band — tracking it with navigation, line-of-sight and rotation.
                /// When false, the action ignores targets entirely and always uses `fallback`.
                public boolean accept_target = true;
                /// Where to aim when no usable target is available — either because targeting
                /// is disabled, or enabled but nothing is acquired / inside the band.
                ///   NONE    — don't fire without a target (original behaviour).
                ///   FORWARD — fire straight ahead along the entity's current facing (turret).
                ///   SELF    — aim at the entity's own position (e.g. self-centred AoE pulses).
                public Fallback fallback = Fallback.NONE;
                public enum Fallback { NONE, FORWARD, SELF }
            }
            /// Target-distance band the goal engages in. All-zero defaults preserve the
            /// original behaviour (`max = spell.range`, `min = 0`, `preferred = max × 0.75`).
            public Range range = new Range();
            /// Pool of cast-animation variant numbers to choose from when a cast begins.
            /// See `MeleeAttack.animation_variants` for selection / fallback semantics.
            public List<Integer> cast_animation_variants = List.of(1);
            /// Pool of release-animation variant numbers to choose from when the spell fires.
            /// Independently rolled from `cast_animation_variants`.
            public List<Integer> release_animation_variants = List.of(1);
            /// Ticks the release animation plays for. Drives client-side auto-stop.
            public int release_animation_duration = 15;

            public SpellCast() {}

            public SpellCast(String spell_id, int cooldown) {
                this.spell_id = spell_id;
                this.cooldown = cooldown;
            }

            /// Engagement-distance configuration for a SpellCast action.
            ///
            /// All fields are FRACTIONS of the spell's effective range — `SpellHelper.getRange(caster, spell)`,
            /// which folds in caster-level modifiers (gear, attributes, etc.). For a spell whose
            /// effective range works out to 16 blocks, `min = 0.5` means "minimum 8 blocks".
            ///
            /// The goal only starts (and only keeps running) when the target sits inside
            /// `[min, max] × effectiveRange`. Once running, the entity navigates to
            /// `preferred × effectiveRange` from the target, and the cast counter only
            /// advances while inside that radius.
            ///
            /// Compose these knobs across multiple SpellCast actions to express layered
            /// behaviour — e.g. a long-range spell with `min` set, plus a short-range
            /// spell with `max > 1` (engage from beyond its own theoretical range), makes
            /// the entity walk in from far to use the short-range option.
            public static class Range {
                /// Minimum engagement distance, as a fraction of effective range. Targets
                /// closer than `min × effectiveRange` make `canStart` fail — useful for
                /// handing close-range fights off to a shorter-range action lower in the
                /// priority list. Default 0 = no minimum.
                public float min = 0F;
                /// Maximum engagement distance, as a fraction of effective range. Targets
                /// farther than `max × effectiveRange` make `canStart` fail. Default 1 =
                /// full effective range. Set above 1 to "walk in from far" — the goal
                /// becomes eligible at a distance the spell can't yet reach, and the
                /// navigation logic closes the gap to `preferred × effectiveRange` before
                /// the cast counter starts advancing.
                public float max = 1F;
                /// Preferred standoff distance, as a fraction of effective range. The
                /// entity navigates to `preferred × effectiveRange` blocks from the target,
                /// and the cast counter only advances while inside that radius. Default 0.75.
                public float preferred = 0.75F;
            }
        }
    }
}
