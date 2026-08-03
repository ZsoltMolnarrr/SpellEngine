package net.spell_engine.api.spell.fx;

import net.spell_engine.api.util.AlwaysGenerate;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/// Describes a single visual effect made of particles.
///
/// The structure is split along the boundary where a particle starts existing:
/// - [#id] selects which registered particle type to spawn, and therefore which
///   factory handles it.
/// - [Particle] is everything one particle needs to look and behave a certain way.
///   It is the complete payload handed to that factory, which is why a single
///   generic factory can serve every particle in the mod. It carries no id of its
///   own, so it can be transmitted and applied wholesale.
/// - [Batch] is everything resolved *before* any particle exists: how many to make,
///   where to place them, and what velocity to hand them. Pure geometry, never seen
///   by the factory.
///
/// Parsed effects must be treated as immutable — the mutable fields exist only for
/// GSON and for bloat-free builder functions. Use [#copy()] before mutating one that
/// came out of a registered spell.
public class ParticleGroupEffect { public ParticleGroupEffect() { }
    /// Id of a registered particle type, e.g. `spell_engine:magic_spell`.
    ///
    /// Vanilla and third-party ids (`minecraft:flame`, `minecraft:crit`, …) are
    /// supported and behave as they do in V1: the [Batch] geometry places and
    /// launches them, and the entire [Particle] block is ignored, because their
    /// factories know nothing about our payload. Around 30 call sites rely on this.
    /// Register a [Particle]-controllable equivalent instead if a vanilla particle
    /// needs colouring, scaling or a lifetime change.
    public String id;

    @AlwaysGenerate
    public Particle particle = new Particle();
    @AlwaysGenerate
    public Batch batch = new Batch();

    // MARK: - Particle

    /// Per-particle appearance and motion — the complete factory payload.
    ///
    /// Deliberately carries no particle id: the id lives on the enclosing
    /// [ParticleGroupEffect] and selects the factory, so this block can be handed
    /// over and applied as one whole structure.
    ///
    /// Fields left at their defaults inherit from the registered particle entry and
    /// from the [Motion] preset, so a typical effect only sets a handful of them.
    ///
    /// Ignored entirely when [ParticleGroupEffect#id] names a particle this mod did
    /// not register.
    public static class Particle { public Particle() { }

        // MARK: Playback

        /// Speed the particle's whole timeline runs at, relative to the timing the
        /// registered entry defines for itself — its texture frame count when
        /// animated, or a static value when not.
        ///
        /// The sprite animation always plays through exactly once across the lifetime,
        /// so one value governs both: `2.5` on a 16 frame entry plays those 16 frames
        /// at 2.5x, over a 6 tick life. Playback rate and lifetime are the same knob
        /// seen from two sides, so there is one field rather than two.
        ///
        /// `1` is the entry's own timing, `2.5` a quicker flash of the same texture,
        /// `0.5` a lingering one. Reusing one entry at several speeds is the point.
        ///
        /// **Negative values run the sequence backwards** at that speed — `-1` is the
        /// entry's own timing in reverse. It flips whatever order the entry considers
        /// normal, so a texture registered with the reverse order flag plays forwards
        /// under a negative value.
        ///
        /// Single-frame entries have no sequence to reverse, so only the magnitude
        /// applies to those.
        ///
        /// `0` would never advance and is treated as `1`.
        public float playback_speed = 1F;

        // MARK: Appearance

        /// Tint, packed RGBA. `-1` leaves the texture untinted.
        /// The alpha component multiplies [#opacity], so a translucent RGBA color
        /// dims the particle — pass `FF` alpha for pure tinting.
        public long color = -1;

        /// Random darkening applied once at spawn, `0..1`.
        /// `0.5` darkens each particle by a random amount up to 50%, which is the
        /// per-particle variation most hand-written factories bake in today.
        public float color_variance = 0F;

        /// Peak opacity — the value held between the fade in and fade out ramps.
        public float opacity = 1F;

        /// Fade envelope, multiplying [#opacity] over the lifetime.
        /// `null` = holds [#opacity] for the whole lifetime, then vanishes.
        @Nullable public Easing.Curve opacity_curve = null;

        /// Size at spawn.
        public float scale = 1F;

        /// Random size variation as a fraction of [#scale]. `0.25` gives each
        /// particle a size in `[0.75, 1.25] * scale`.
        public float scale_variance = 0F;

        /// Size at death, as a multiple of the size the particle spawned at.
        /// `0.5` shrinks to half, `2` doubles, `1` = no change.
        /// Relative rather than absolute so it composes with [#scale_variance] —
        /// a particle randomised to `0.6` still doubles to `1.2`.
        /// Only read when [#scale_easing] is set.
        public float scale_multiplier = 1F;

        /// Interpolates [#scale] to `scale * scale_multiplier` across the lifetime,
        /// shaped by this curve. The curve changes the path taken, never the endpoint.
        /// `null` = holds [#scale] for the whole lifetime, which is the common case.
        /// Replaces per-tick growth.
        @Nullable public Easing scale_easing = null;

        /// How the sprite quad is oriented in the world.
        /// `null` inherits the registered entry's default ([Facing#CAMERA] if the
        /// entry defines none).
        @Nullable public Facing facing = null;

        /// When true the particle renders at full brightness, ignoring world light.
        /// Purely a brightness override — it does not affect [#render].
        /// `null` inherits the registered entry's default (`true` if the entry
        /// defines none).
        @Nullable public Boolean glow = null;

        /// Which particle render sheet to draw on.
        /// `null` inherits the registered entry's default ([Render#TRANSLUCENT] if
        /// the entry defines none).
        @Nullable public Render render = null;

        // MARK: Motion

        /// Preset supplying the default [#gravity], [#drag] and spawn velocity shaping.
        /// `null` inherits the registered entry's default ([Motion#STATIC] if the
        /// entry defines none).
        @Nullable public Motion motion = null;

        /// Downward acceleration per tick. Negative values rise.
        /// `null` inherits from [#motion].
        @Nullable public Float gravity = null;

        /// Fraction of velocity retained each tick, `0..1`.
        /// `1` keeps full speed forever, `0.9` decays quickly.
        /// `null` inherits from [#motion].
        @Nullable public Float drag = null;

        /// When true the particle collides with blocks instead of passing through.
        public boolean collides = false;

        /// How the particle tracks the entity it was spawned from.
        public Attachment attachment = Attachment.NONE;

        // MARK: Builders

        public Particle playbackSpeed(float playback_speed) { this.playback_speed = playback_speed; return this; }
        /// Flips the sequence direction, keeping the current speed.
        public Particle reversed() { this.playback_speed = -this.playback_speed; return this; }
        public Particle color(long color) { this.color = color; return this; }
        public Particle colorVariance(float color_variance) { this.color_variance = color_variance; return this; }
        public Particle opacity(float opacity) { this.opacity = opacity; return this; }
        public Particle opacityCurve(@Nullable Easing.Curve opacity_curve) { this.opacity_curve = opacity_curve; return this; }
        public Particle opacity(float opacity, @Nullable Easing.Curve opacity_curve) { this.opacity = opacity; this.opacity_curve = opacity_curve; return this; }
        public Particle scale(float scale) { this.scale = scale; return this; }
        public Particle scale(float scale, float variance) { this.scale = scale; this.scale_variance = variance; return this; }
        /// Scales linearly to `scale_multiplier` times its spawn size across the lifetime.
        public Particle scaleMultiplier(float scale_multiplier) { this.scale_multiplier = scale_multiplier; this.scale_easing = Easing.LINEAR; return this; }
        public Particle scaleMultiplier(float scale_multiplier, Easing easing) { this.scale_multiplier = scale_multiplier; this.scale_easing = easing; return this; }
        public Particle scaleEasing(@Nullable Easing scale_easing) { this.scale_easing = scale_easing; return this; }
        public Particle facing(@Nullable Facing facing) { this.facing = facing; return this; }
        public Particle glow(@Nullable Boolean glow) { this.glow = glow; return this; }
        public Particle render(@Nullable Render render) { this.render = render; return this; }
        public Particle motion(@Nullable Motion motion) { this.motion = motion; return this; }
        public Particle gravity(@Nullable Float gravity) { this.gravity = gravity; return this; }
        public Particle drag(@Nullable Float drag) { this.drag = drag; return this; }
        public Particle collides(boolean collides) { this.collides = collides; return this; }
        public Particle attachment(Attachment attachment) { this.attachment = attachment; return this; }

        public Particle copy() {
            var copy = new Particle();
            copy.playback_speed = this.playback_speed;
            copy.color = this.color;
            copy.color_variance = this.color_variance;
            copy.opacity = this.opacity;
            copy.opacity_curve = this.opacity_curve != null ? this.opacity_curve.copy() : null;
            copy.scale = this.scale;
            copy.scale_variance = this.scale_variance;
            copy.scale_multiplier = this.scale_multiplier;
            copy.scale_easing = this.scale_easing;
            copy.facing = this.facing;
            copy.glow = this.glow;
            copy.render = this.render;
            copy.motion = this.motion;
            copy.gravity = this.gravity;
            copy.drag = this.drag;
            copy.collides = this.collides;
            copy.attachment = this.attachment;
            return copy;
        }
    }

    /// How a particle's sprite quad is oriented in the world.
    /// Replaces the V1 practice of registering the same texture twice
    /// (`area_effect_574` / `aura_effect_574`) just to render it two ways.
    public enum Facing {
        /// Billboard, always square to the camera. V1 `Orientation.VERTICAL` / `aura_*`.
        CAMERA,
        /// Flat on the ground plane, facing up. V1 `Orientation.HORIZONTAL` / `area_*`.
        GROUND,
        /// Billboard that only rotates around the Y axis, staying upright.
        UPRIGHT,
        /// Aligned with the particle's direction of travel — streaks and stripes.
        VELOCITY
    }

    /// Which vanilla particle render sheet the particle draws on.
    /// Names the sheet directly rather than deriving it from other fields —
    /// [Particle#glow] is a brightness override only.
    public enum Render {
        /// `PARTICLE_SHEET_OPAQUE`. V1: `roots`.
        OPAQUE,
        /// `PARTICLE_SHEET_TRANSLUCENT`. V1 default for magic, area/aura and sign particles.
        TRANSLUCENT,
        /// `PARTICLE_SHEET_LIT`. V1 default for elemental particles — flames,
        /// electric arcs, frost shard, smoke.
        LIT
    }

    /// Preset motion behaviour, supplying defaults for gravity, drag and how the
    /// batch-provided spawn velocity is shaped.
    /// Explicit [Particle#gravity] / [Particle#drag] override the preset.
    public enum Motion {
        /// Keeps its spawn velocity unchanged. No gravity, no drag.
        STATIC,
        /// Gentle randomised drift with mild drag.
        FLOAT,
        /// Rises steadily against gravity.
        ASCEND,
        /// Strong drag — travels a short distance then comes to a halt.
        DECELERATE,
        /// Thrown outward hard, then pulled down by gravity.
        BURST
    }

    /// How a particle tracks the entity it was spawned from.
    public enum Attachment {
        /// Free in the world, unaffected by the source entity after spawning.
        NONE,
        /// Moves with the entity, preserving the offset it spawned at.
        POSITION,
        /// Like [#POSITION], and additionally scales with the entity's own scale.
        POSITION_SCALED
    }

    // MARK: - Batch

    /// How many particles to spawn, where to place them, and what velocity to give them.
    /// Resolved before any particle exists — never reaches the particle factory.
    public static class Batch { public Batch() { }
        /// Number of particles to spawn. Values below `1` are treated as a spawn
        /// chance, e.g. `0.25` spawns one particle a quarter of the time.
        @AlwaysGenerate
        public float count = 1F;

        /// Spawn placement and initial velocity pattern.
        @AlwaysGenerate
        public Shape shape = Shape.CIRCLE;

        /// How the base spawn point is located.
        @AlwaysGenerate
        public Anchor anchor = Anchor.ENTITY;

        /// Vertical offset from [#anchor], in units of the source entity's height.
        /// `0` = feet, `0.5` = center, `1` = top of the head, `1.5` = above the head.
        /// Replaces the V1 `Origin` enum's height cases — note V1 `FEET` is
        /// `height * 0.1`, so it ports to `0.1`, not `0`.
        public float vertical_origin = 0.5F;

        /// Whether [#shape] is laid out along world axes or rotated into the
        /// source's look direction.
        public Alignment alignment = Alignment.WORLD;

        /// Degrees the whole spawn pattern rotates per tick, around its forward axis
        /// (the aim axis under [Alignment#LOOK], the Y axis under [Alignment#WORLD]).
        ///
        /// Because the angle advances with world time, successive spawns of a
        /// repeating effect trace a helix rather than a fixed pattern — this is how
        /// Archers' Magic Arrow and Paladins' Holy orb get their spiraling wake.
        /// `0` = fixed pattern. Negative values counter-rotate.
        /// V1 `roll`, which was a rate despite the name.
        public float roll_per_tick = 0F;

        /// Starting angle of [#roll_per_tick], in degrees. Two otherwise identical
        /// effects `180` apart trace a double helix. V1 `roll_offset`.
        public float roll_offset = 0F;

        /// Lower bound of the randomised initial speed.
        public float min_speed = 0F;

        /// Upper bound of the randomised initial speed.
        /// Negative bounds invert the pattern, same as [#invert].
        public float max_speed = 0F;

        /// Spread in degrees, for [Shape#CONE].
        public float angle = 0F;

        /// Radial offset added on top of the source entity's own radius.
        public float extent = 0F;

        /// Multiplier on the source entity's width when placing particles.
        /// `0` makes [#extent] an absolute radius, independent of entity size —
        /// replacing the V1 `EXTENT_TRESHOLD` sentinel. `2` reproduces V1 `WIDE_PIPE`.
        public float width_factor = 1F;

        /// Distance travelled along the spawn direction before the particle appears.
        public float pre_travel = 0F;

        /// Flips the initial velocity, turning outward patterns inward.
        public boolean invert = false;

        // MARK: Builders

        public Batch count(float count) { this.count = count; return this; }
        public Batch shape(Shape shape) { this.shape = shape; return this; }
        public Batch anchor(Anchor anchor) { this.anchor = anchor; return this; }
        public Batch verticalOrigin(float vertical_origin) { this.vertical_origin = vertical_origin; return this; }
        public Batch origin(Anchor anchor, float vertical_origin) { this.anchor = anchor; this.vertical_origin = vertical_origin; return this; }
        public Batch alignment(Alignment alignment) { this.alignment = alignment; return this; }
        public Batch roll(float roll_per_tick) { this.roll_per_tick = roll_per_tick; return this; }
        public Batch roll(float roll_per_tick, float roll_offset) { this.roll_per_tick = roll_per_tick; this.roll_offset = roll_offset; return this; }
        public Batch rollOffset(float roll_offset) { this.roll_offset = roll_offset; return this; }
        public Batch speed(float speed) { this.min_speed = speed; this.max_speed = speed; return this; }
        public Batch speed(float min_speed, float max_speed) { this.min_speed = min_speed; this.max_speed = max_speed; return this; }
        public Batch angle(float angle) { this.angle = angle; return this; }
        public Batch extent(float extent) { this.extent = extent; return this; }
        public Batch widthFactor(float width_factor) { this.width_factor = width_factor; return this; }
        public Batch preTravel(float pre_travel) { this.pre_travel = pre_travel; return this; }
        public Batch invert(boolean invert) { this.invert = invert; return this; }

        public Batch copy() {
            var copy = new Batch();
            copy.count = this.count;
            copy.shape = this.shape;
            copy.anchor = this.anchor;
            copy.vertical_origin = this.vertical_origin;
            copy.alignment = this.alignment;
            copy.roll_per_tick = this.roll_per_tick;
            copy.roll_offset = this.roll_offset;
            copy.min_speed = this.min_speed;
            copy.max_speed = this.max_speed;
            copy.angle = this.angle;
            copy.extent = this.extent;
            copy.width_factor = this.width_factor;
            copy.pre_travel = this.pre_travel;
            copy.invert = this.invert;
            return copy;
        }
    }

    /// Spawn placement and initial velocity pattern.
    /// V1 `WIDE_PIPE` is gone — it is `PIPE` with [Batch#width_factor] `2`.
    public enum Shape {
        /// Outward along the horizontal plane, in a random direction.
        CIRCLE,
        /// Upward, from a random point *within* the radius.
        PILLAR,
        /// Upward, from a random point *on* the radius.
        PIPE,
        /// Outward in a fully random 3D direction.
        SPHERE,
        /// Forward along a single direction, spread by [Batch#angle].
        CONE,
        /// Straight forward.
        LINE,
        /// Straight up.
        LINE_VERTICAL
    }

    /// How the base spawn point of a batch is located.
    /// Combined with [Batch#vertical_origin] this replaces the V1 `Origin` enum.
    public enum Anchor {
        /// The source entity's position, offset by [Batch#vertical_origin].
        ENTITY,
        /// The first solid block below the source. V1 `Origin.GROUND`.
        GROUND,
        /// The caster's spell launch point, in front of the casting hand.
        /// V1 `Origin.LAUNCH_POINT`.
        LAUNCH_POINT
    }

    /// Whether a batch's [Shape] is laid out along world axes or rotated into the
    /// source's look direction. V1 `Rotation.LOOK`, which the source itself noted
    /// should have been called an orientation.
    public enum Alignment {
        /// World axes — the pattern points the same way regardless of the caster.
        WORLD,
        /// Rotated into the source's yaw and pitch.
        LOOK
    }

    // MARK: - Builders

    /// Authoring goes through [ParticleGroupBuilder], which owns the starting points
    /// (`of`, `magic`, `zone`, `aura`), the named appearance and motion methods, and
    /// the [ParticleGroupBuilder.Batches] presets. What remains here are the two
    /// escape hatches for reaching a finished effect's blocks, plus [#copy()].

    /// Configures the [Particle] block in place.
    ///
    /// ```java
    /// ParticleGroupBuilder.of(SpellEngineParticles.flame)
    ///     .batch(Batches.impact(12, 0.3F))
    ///     .particle(p -> p.collides(true));
    /// ```
    public ParticleGroupEffect particle(Consumer<Particle> configure) {
        configure.accept(this.particle);
        return this;
    }

    /// Configures the [Batch] block in place.
    public ParticleGroupEffect batch(Consumer<Batch> configure) {
        configure.accept(this.batch);
        return this;
    }

    /// Deep copy. Use before mutating an effect obtained from a registered spell,
    /// which must be treated as immutable.
    public ParticleGroupEffect copy() {
        var copy = new ParticleGroupEffect();
        copy.id = this.id;
        copy.particle = this.particle.copy();
        copy.batch = this.batch.copy();
        return copy;
    }
}
