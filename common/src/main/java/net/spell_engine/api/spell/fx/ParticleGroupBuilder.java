package net.spell_engine.api.spell.fx;

import net.minecraft.util.Identifier;
import net.spell_engine.client.util.Color;
import net.spell_engine.fx.SpellEngineParticles;

import java.util.function.Consumer;

/// Fluent construction of [ParticleGroupEffect]s for content mods.
///
/// The two phases mirror the structure being built, and the split is strict:
///
/// ```java
/// ParticleGroupBuilder.magic(MagicParticles.arcane, Motion.ASCEND)  // what a particle is
///     .color(Color.ARCANE)                                          // ...and how it looks
///     .batch(Batches.casting(8, 0.1F));                             // how many, and where
/// ```
///
/// **Named methods configure the particle. [#batch] configures the batch.** Nothing
/// batch-side — count, shape, placement, velocity, spread — has a shortcut on this
/// class, so there is never a question of which of two ways to reach a field. The
/// particle methods write straight into the effect, so their order never matters.
///
/// [#batch] closes the builder and returns the finished effect.
///
/// ## Entry defaults
///
/// A builder only ever sets what you ask it to. Everything else stays unset, and the
/// registered [SpellEngineParticles.Entry] fills it in at spawn time — its texture's
/// lifetime, its render sheet, its base scale, a zone texture's `GROUND` facing.
/// Anything you set here overrides that; `scale`, `opacity` and `playbackSpeed`
/// multiply with it rather than replacing it, so `scale(2F)` means twice the entry's
/// own size, whatever that is.
///
/// ## Presets
///
/// [Batches] holds the common batch layouts as reusable `Consumer<Batch>` values, so
/// convenience arrives through the same door as everything else, and composes:
///
/// ```java
/// .batch(Batches.impact(10, 0.3F).andThen(b -> b.extent(0.5F)))
/// ```
///
/// ## Staying flexible
///
/// [#batch] returns a [ParticleGroupEffect], so anything this class does not cover
/// stays reachable on the result:
///
/// ```java
/// ParticleGroupBuilder.of(SpellEngineParticles.flame)
///     .batch(Batches.impact(12, 0.3F))
///     .particle(p -> p.collides(true));
/// ```
public class ParticleGroupBuilder {
    private final ParticleGroupEffect effect;

    private ParticleGroupBuilder(String id) {
        this.effect = new ParticleGroupEffect();
        this.effect.id = id;
    }

    // MARK: - Starting points

    public static ParticleGroupBuilder of(String id) {
        return new ParticleGroupBuilder(id);
    }

    public static ParticleGroupBuilder of(Identifier id) {
        return new ParticleGroupBuilder(id.toString());
    }

    public static ParticleGroupBuilder of(SpellEngineParticles.Entry entry) {
        return new ParticleGroupBuilder(entry.id().toString());
    }

    /// A magic particle, moving however you ask.
    ///
    /// V1 registered the 8x4 product of shapes and motions as 32 separate particle
    /// types, so motion was fixed at registration. Choosing it here is the whole
    /// reason that collapsed to 8 — see [SpellEngineParticles.MagicParticles].
    public static ParticleGroupBuilder magic(SpellEngineParticles.Entry entry, ParticleGroupEffect.Motion motion) {
        return of(entry).motion(motion);
    }

    public static ParticleGroupBuilder magic(SpellEngineParticles.Entry entry, ParticleGroupEffect.Motion motion, Color color) {
        return magic(entry, motion).color(color);
    }

    /// A flat effect lying on the ground plane — runes, circles, shockwaves.
    /// Pair with [Batches#ground(float)] to place it on the floor.
    ///
    /// The zone entries already default to [ParticleGroupEffect.Facing#GROUND], so
    /// this is mostly for making the intent explicit, and for turning a non-zone
    /// texture into one.
    public static ParticleGroupBuilder zone(SpellEngineParticles.Entry entry) {
        return of(entry).facing(ParticleGroupEffect.Facing.GROUND);
    }

    public static ParticleGroupBuilder zone(Identifier id) {
        return of(id).facing(ParticleGroupEffect.Facing.GROUND);
    }

    /// An upright effect that follows and scales with its entity — the `aura_*` role.
    ///
    /// The same entries work as [#zone] effects; orientation is no longer decided at
    /// registration, which is what lets one texture serve both.
    public static ParticleGroupBuilder aura(SpellEngineParticles.Entry entry) {
        return of(entry)
                .facing(ParticleGroupEffect.Facing.CAMERA)
                .attached(ParticleGroupEffect.Attachment.POSITION_SCALED);
    }

    public static ParticleGroupBuilder aura(Identifier id) {
        return of(id)
                .facing(ParticleGroupEffect.Facing.CAMERA)
                .attached(ParticleGroupEffect.Attachment.POSITION_SCALED);
    }

    // MARK: - Appearance

    public ParticleGroupBuilder color(long rgba) {
        effect.particle.color(rgba);
        return this;
    }

    public ParticleGroupBuilder color(Color color) {
        return color(color.toRGBA());
    }

    /// Random darkening at spawn, `0..1`. Each particle is multiplied by a random
    /// factor in `[1 - amount, 1]`, giving a batch visible internal variation.
    public ParticleGroupBuilder colorVariance(float amount) {
        effect.particle.colorVariance(amount);
        return this;
    }

    /// Multiplies the entry's own size.
    public ParticleGroupBuilder scale(float scale) {
        effect.particle.scale(scale);
        return this;
    }

    public ParticleGroupBuilder scale(float scale, float variance) {
        effect.particle.scale(scale, variance);
        return this;
    }

    /// Grows or shrinks to `multiplier` times the spawn size over the lifetime.
    public ParticleGroupBuilder scaleTo(float multiplier, Easing easing) {
        effect.particle.scaleMultiplier(multiplier, easing);
        return this;
    }

    /// Multiplies the entry's own opacity.
    public ParticleGroupBuilder opacity(float opacity) {
        effect.particle.opacity(opacity);
        return this;
    }

    /// Holds full opacity for `hold` of the lifetime, then fades away.
    public ParticleGroupBuilder fadeOut(float hold, Easing easing) {
        effect.particle.opacityCurve(Easing.Curve.fadeOut(easing, hold));
        return this;
    }

    /// Fades in, holds for `hold` of the lifetime, then fades out.
    /// `hold = 0.4` with linear ramps reproduces V1's `Fading.IN_OUT` exactly.
    public ParticleGroupBuilder fadeInOut(float hold, Easing easing) {
        effect.particle.opacityCurve(Easing.Curve.fadeInOut(easing, easing, hold));
        return this;
    }

    /// Scales the particle's whole timeline — lifetime and sprite animation together.
    /// Negative values run the sequence backwards.
    public ParticleGroupBuilder playbackSpeed(float speed) {
        effect.particle.playbackSpeed(speed);
        return this;
    }

    /// Random lifetime spread, `0..1`. `0.5` gives each particle a life in
    /// `[0.5x, 1.5x]`, so a batch thins out instead of vanishing all at once.
    public ParticleGroupBuilder lifetimeVariance(float amount) {
        effect.particle.lifetimeVariance(amount);
        return this;
    }

    public ParticleGroupBuilder facing(ParticleGroupEffect.Facing facing) {
        effect.particle.facing(facing);
        return this;
    }

    public ParticleGroupBuilder glow(boolean glow) {
        effect.particle.glow(glow);
        return this;
    }

    public ParticleGroupBuilder render(ParticleGroupEffect.Render render) {
        effect.particle.render(render);
        return this;
    }

    // MARK: - Motion

    public ParticleGroupBuilder motion(ParticleGroupEffect.Motion motion) {
        effect.particle.motion(motion);
        return this;
    }

    /// Overrides the motion preset's gravity. Negative values rise.
    public ParticleGroupBuilder gravity(float gravity) {
        effect.particle.gravity(gravity);
        return this;
    }

    /// Overrides the motion preset's drag — the fraction of velocity kept each tick.
    public ParticleGroupBuilder drag(float drag) {
        effect.particle.drag(drag);
        return this;
    }

    public ParticleGroupBuilder collides() {
        effect.particle.collides(true);
        return this;
    }

    /// Makes particles ride the entity they spawned from.
    public ParticleGroupBuilder attached() {
        return attached(ParticleGroupEffect.Attachment.POSITION);
    }

    public ParticleGroupBuilder attached(ParticleGroupEffect.Attachment attachment) {
        effect.particle.attachment(attachment);
        return this;
    }

    /// Escape hatch for particle fields without a named method here.
    public ParticleGroupBuilder particle(Consumer<ParticleGroupEffect.Particle> configure) {
        configure.accept(effect.particle);
        return this;
    }

    // MARK: - Closing

    /// Configures the batch and returns the finished effect.
    ///
    /// The only way to reach batch fields from this builder, by design. Use a
    /// [Batches] preset, a lambda, or a preset composed with one via `andThen`.
    public ParticleGroupEffect batch(Consumer<ParticleGroupEffect.Batch> configure) {
        configure.accept(effect.batch);
        return effect;
    }

    /// Closes the builder with the default batch — a single particle at the source's
    /// centre, with no velocity.
    public ParticleGroupEffect batch() {
        return effect;
    }

    // MARK: - Batch presets

    /// Common batch layouts, as composable `Consumer<Batch>` values.
    ///
    /// Each sets only what its use case implies, so composing with `andThen` to
    /// adjust or override reads naturally:
    ///
    /// ```java
    /// .batch(Batches.casting(6, 0.1F).andThen(b -> b.extent(-0.2F)))
    /// ```
    public static class Batches {
        /// Vertical origins matching V1's `Origin` cases, as fractions of the source
        /// entity's height.
        ///
        /// [#FEET] is `0.1`, not `0` — V1's `Origin.FEET` sat slightly above the
        /// entity's position, and porting it as `0` sinks particles into the floor.
        public static final float FEET = 0.1F;
        public static final float CENTER = 0.5F;
        public static final float HEAD = 1.0F;
        public static final float OVER_HEAD = 1.5F;

        /// Burst outward from the target's centre. The standard impact effect.
        public static Consumer<ParticleGroupEffect.Batch> impact(float count, float speed) {
            return b -> b.shape(ParticleGroupEffect.Shape.SPHERE)
                    .count(count).speed(speed * 0.5F, speed)
                    .verticalOrigin(CENTER);
        }

        /// Rising swirl around the caster's feet, at double body radius.
        /// V1 expressed that widened radius as a separate `WIDE_PIPE` shape.
        public static Consumer<ParticleGroupEffect.Batch> casting(float count, float speed) {
            return b -> b.shape(ParticleGroupEffect.Shape.PIPE)
                    .count(count).speed(speed * 0.5F, speed)
                    .verticalOrigin(FEET).widthFactor(2F);
        }

        /// Trailing wake around a projectile, oriented to its flight path.
        public static Consumer<ParticleGroupEffect.Batch> travel(float count, float speed) {
            return b -> b.shape(ParticleGroupEffect.Shape.CIRCLE)
                    .count(count).speed(speed * 0.5F, speed)
                    .verticalOrigin(CENTER)
                    .alignment(ParticleGroupEffect.Alignment.LOOK);
        }

        /// A column rising from within the source's footprint — cloud presence FX.
        public static Consumer<ParticleGroupEffect.Batch> cloud(float count, float speed) {
            return b -> b.shape(ParticleGroupEffect.Shape.PILLAR)
                    .count(count).speed(speed * 0.5F, speed)
                    .verticalOrigin(FEET);
        }

        /// Expanding ring along the floor, thrown outward from the source's feet.
        /// `preTravel` starts it away from the centre — stack a few at rising
        /// distances for a shockwave.
        public static Consumer<ParticleGroupEffect.Batch> shockwave(float count, float speed, float preTravel) {
            return b -> b.shape(ParticleGroupEffect.Shape.CIRCLE)
                    .count(count).speed(speed)
                    .verticalOrigin(FEET).preTravel(preTravel);
        }

        /// Placed flat on the floor below the source, motionless.
        public static Consumer<ParticleGroupEffect.Batch> ground(float count) {
            return b -> b.shape(ParticleGroupEffect.Shape.NONE)
                    .count(count)
                    .anchor(ParticleGroupEffect.Anchor.GROUND);
        }

        /// Placed on the source itself, motionless — auras, explosions, any effect
        /// that is one billboard sitting where you put it rather than a spray.
        public static Consumer<ParticleGroupEffect.Batch> placed(float count) {
            return b -> b.shape(ParticleGroupEffect.Shape.NONE)
                    .count(count)
                    .verticalOrigin(CENTER);
        }

        /// @deprecated renamed to [#placed(float)], which says what it does.
        @Deprecated
        public static Consumer<ParticleGroupEffect.Batch> still(float count) {
            return placed(count);
        }

        /// A sign or icon drifting up above the entity's head.
        public static Consumer<ParticleGroupEffect.Batch> popUp() {
            return b -> b.shape(ParticleGroupEffect.Shape.LINE_VERTICAL)
                    .count(1).speed(0.05F, 0.1F)
                    .verticalOrigin(OVER_HEAD);
        }

        /// One strand of a spiralling wake. Two of these `180` apart form a double
        /// helix — how the Holy orb and Magic Arrow wakes are built.
        public static Consumer<ParticleGroupEffect.Batch> helix(float count, float speed,
                                                                float degreesPerTick, float offset) {
            return b -> b.shape(ParticleGroupEffect.Shape.LINE)
                    .count(count).speed(speed * 0.75F, speed)
                    .verticalOrigin(CENTER)
                    .alignment(ParticleGroupEffect.Alignment.LOOK)
                    .roll(degreesPerTick, offset);
        }

        /// Forward in a spread cone. `angle` is the total spread in degrees.
        public static Consumer<ParticleGroupEffect.Batch> cone(float count, float speed, float angle) {
            return b -> b.shape(ParticleGroupEffect.Shape.CONE)
                    .count(count).speed(speed * 0.5F, speed)
                    .angle(angle)
                    .verticalOrigin(CENTER)
                    .alignment(ParticleGroupEffect.Alignment.LOOK);
        }
    }
}
