package net.spell_engine.api.spell.fx;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/// Namespace for reusable FX bundles.
///
/// [Spell][net.spell_engine.api.spell.Spell] used to repeat one shape over and over:
/// particles and models describing the same moment, declared as two loose fields and
/// re-spelled by hand at each site. [Visuals] gives that pairing a single definition.
///
/// ## Only visuals live here
///
/// Sound and light stay as their own fields next to a [Visuals], rather than
/// becoming `Visuals + sound`, `Visuals + light`, `Visuals + sound + light`
/// variants. One grouping object composes with any number of siblings; a family of
/// pre-combined ones multiplies out and forces a rename every time a site gains or
/// loses an FX kind.
///
/// Every one-shot FX site in [Spell][net.spell_engine.api.spell.Spell] therefore
/// spells the same pair: a `visuals` field and, where the moment has audio of its
/// own, a `sound` field beside it. Continuous presence FX (a cloud's ambient
/// particles, a projectile's travel trail, casting particles) stay as plain lists —
/// they describe an ongoing state rather than a moment, and are emitted on a
/// different schedule.
///
/// ## Authoring
///
/// ```java
/// beam.block_hit = Fx.Visuals.of(
///         ParticleGroupBuilder.of(SpellEngineParticles.smoke_medium)
///                 .batch(ParticleGroupBuilder.Batches.impact(6, 0.2F)));
/// ```
public class Fx {

    /// Particles and models, no audio. What to show, with nothing to hear.
    ///
    /// Used where the sound is owned by something else — a summon group plays one
    /// sound for the group but spawns visuals per entity, and a teleport's departure
    /// and arrival share a single sound between two visual bundles.
    ///
    /// Succeeds the standalone `VFX` class.
    public static class Visuals { public Visuals() { }
        public List<ParticleGroupEffect> particles = List.of();
        public List<ModelEffect> models = List.of();

        public static Visuals of(ParticleGroupEffect... particles) {
            var fx = new Visuals();
            fx.particles = List.of(particles);
            return fx;
        }

        public Visuals particles(ParticleGroupEffect... particles) {
            this.particles = List.of(particles);
            return this;
        }

        public Visuals models(ModelEffect... models) {
            this.models = List.of(models);
            return this;
        }

        public boolean isEmpty() {
            return particles.isEmpty() && models.isEmpty();
        }

        /// Resolves every [ScaleWith] declaration in this bundle against `context`,
        /// returning a bundle whose sizes are final.
        ///
        /// Effects declaring [ScaleWith#NONE] (the default) pass through untouched and
        /// uncopied; only the ones asking to be scaled are copied before their size is
        /// multiplied. A bundle where nothing asks for scaling returns itself, so the
        /// common case allocates nothing.
        ///
        /// This is what makes mixed bundles work: scaled and unscaled effects live in the
        /// same two lists and are emitted in one batch, because the declaration sits on the
        /// individual effect rather than on the bundle.
        public Visuals resolved(Context context) {
            var scaledParticles = Fx.scaleParticles(particles, context);
            var scaledModels = Fx.scaleModels(models, context);
            if (scaledParticles == particles && scaledModels == models) {
                return this;
            }
            var resolved = new Visuals();
            resolved.particles = scaledParticles;
            resolved.models = scaledModels;
            return resolved;
        }

        public Visuals copy() {
            var copy = new Visuals();
            copy.particles = this.particles;
            copy.models = this.models;
            return copy;
        }
    }

    // MARK: - Contextual scaling

    /// A magnitude, resolved where the effect is emitted, that an effect's authored
    /// `scale` is multiplied by.
    ///
    /// Declared on the effect itself — [ParticleGroupEffect.Particle#scale_with] and
    /// [ModelEffect#scale_with] — so a single bundle can mix scaled and unscaled
    /// effects. The authored `scale` always acts as a coefficient: final size is
    /// `scale * resolved value`, never a replacement.
    ///
    /// Not every variable is meaningful everywhere, which is the point of resolving
    /// through a [Context]: an emission site binds only the variables that genuinely
    /// describe it, and an effect asking for one the site cannot supply falls back to a
    /// coefficient of `1` with a one-time warning rather than inventing a number.
    public enum ScaleWith {
        /// Authored size is used as-is. The default.
        NONE,
        /// The spell's effective range in blocks, as the caster sees it — including the
        /// melee range mechanic and any range modifiers in play. Bound where a spell's
        /// range describes the size of what is being drawn (a release shockwave sized to
        /// the reach of the swing that produced it); unbound at sites anchored on a
        /// target, where the caster's range says nothing about how big the effect is.
        RANGE
    }

    /// The magnitudes an emission site can legitimately supply, bound at the callsite.
    ///
    /// A site constructs the context describing what it actually knows — a release
    /// knows the spell's range, an impact landing on a distant target does not — and
    /// unbound variables resolve to a neutral `1`. This keeps the set of valid
    /// variables per site visible in code, instead of implied by which fields happen to
    /// exist on the spell.
    public record Context(@Nullable Float range) {
        /// Binds nothing. For sites where no magnitude is semantically available.
        public static final Context NONE = new Context(null);

        public static Context ofRange(float range) {
            return new Context(range);
        }

        /// The multiplier `scaleWith` asks for, or `1` when this site does not bind it.
        /// `effectId` only labels the warning emitted in the unbound case.
        public float resolve(ScaleWith scaleWith, @Nullable String effectId) {
            if (scaleWith == null || scaleWith == ScaleWith.NONE) {
                return 1F;
            }
            if (scaleWith == ScaleWith.RANGE) {
                if (range != null) {
                    return range;
                }
            }
            warnUnbound(scaleWith, effectId);
            return 1F;
        }
    }

    private static final Set<String> warnedUnbound = ConcurrentHashMap.newKeySet();

    private static void warnUnbound(ScaleWith scaleWith, @Nullable String effectId) {
        var key = scaleWith.name() + "|" + effectId;
        if (warnedUnbound.add(key)) {
            System.err.println("[SpellEngine] FX effect '" + effectId + "' asks to scale with "
                    + scaleWith + ", which is not available where it is emitted. Using scale as authored.");
        }
    }

    /// Applies [ScaleWith] to every particle effect asking for it, copying only those.
    /// Returns the input list unchanged (same instance) when nothing asks for scaling.
    public static List<ParticleGroupEffect> scaleParticles(List<ParticleGroupEffect> effects, Context context) {
        if (effects == null || effects.isEmpty()) {
            return effects;
        }
        ArrayList<ParticleGroupEffect> scaled = null;
        for (int i = 0; i < effects.size(); i++) {
            var effect = effects.get(i);
            var scaleWith = effect.particle.scale_with;
            if (scaleWith == null || scaleWith == ScaleWith.NONE) {
                if (scaled != null) { scaled.add(effect); }
                continue;
            }
            if (scaled == null) {
                scaled = new ArrayList<>(effects.size());
                scaled.addAll(effects.subList(0, i));
            }
            var copy = effect.copy();
            copy.particle.scale *= context.resolve(scaleWith, effect.id);
            copy.particle.scale_with = ScaleWith.NONE; // resolved; nothing downstream should re-apply it
            scaled.add(copy);
        }
        return scaled != null ? scaled : effects;
    }

    /// Applies [ScaleWith] to every model effect asking for it, copying only those.
    /// Returns the input list unchanged (same instance) when nothing asks for scaling.
    public static List<ModelEffect> scaleModels(List<ModelEffect> effects, Context context) {
        if (effects == null || effects.isEmpty()) {
            return effects;
        }
        ArrayList<ModelEffect> scaled = null;
        for (int i = 0; i < effects.size(); i++) {
            var effect = effects.get(i);
            var scaleWith = effect.scale_with;
            if (scaleWith == null || scaleWith == ScaleWith.NONE) {
                if (scaled != null) { scaled.add(effect); }
                continue;
            }
            if (scaled == null) {
                scaled = new ArrayList<>(effects.size());
                scaled.addAll(effects.subList(0, i));
            }
            var copy = effect.copy();
            copy.scale *= context.resolve(scaleWith, effect.model_id);
            copy.scale_with = ScaleWith.NONE;
            scaled.add(copy);
        }
        return scaled != null ? scaled : effects;
    }
}
