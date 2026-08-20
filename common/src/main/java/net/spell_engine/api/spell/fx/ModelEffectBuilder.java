package net.spell_engine.api.spell.fx;

import net.spell_engine.api.render.LightEmission;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Fluent builder for {@link ModelEffect}.
 *
 * <p>Primitives ({@link #scaleIn}, {@link #scaleOut}, {@link #rotate}, {@link #orbit}) are
 * composable — call any combination in any order. Internal mechanics (base-scale trick for
 * scale-from-zero, rotate-before-translate ordering for correct orbital motion) are handled
 * automatically.</p>
 *
 * <pre>{@code
 * // Single orbiting wave that scales in/out with the orbit
 * ModelEffect fx = ModelEffectBuilder.create("my_mod:spell_effect/wave")
 *     .followEntity().duration(20).light(LightEmission.GLOW)
 *     .orbit(2.0F, 360F, 0, 20, ModelEffect.Easing.EASE_IN_OUT_CUBIC)
 *     .scaleIn(0, 10, ModelEffect.Easing.EASE_OUT_CUBIC)
 *     .scaleOut(10, 20, ModelEffect.Easing.EASE_IN_CUBIC)
 *     .build();
 * }</pre>
 *
 * <p>For common multi-instance patterns see {@link Preset}.</p>
 */
public class ModelEffectBuilder {

    private final ModelEffect effect;
    private boolean baseScaleInjected;

    private ModelEffectBuilder(ModelEffect effect) {
        this.effect = effect;
        this.baseScaleInjected = !effect.initial.isEmpty() && isBaseScale(effect.initial.get(0));
    }

    public static ModelEffectBuilder create(String modelId) {
        var e = new ModelEffect();
        e.model_id = modelId;
        e.initial = new ArrayList<>();
        e.animations = new ArrayList<>();
        return new ModelEffectBuilder(e);
    }

    // --- Basic properties ---

    public ModelEffectBuilder scale(float scale) { effect.scale = scale; return this; }
    /** Sizes the model by a magnitude resolved where it is emitted, with {@link #scale} kept as a
     * coefficient on top of it. Resolved only for models emitted as part of an {@link Fx.Visuals}. */
    public ModelEffectBuilder scaleWith(Fx.ScaleWith scaleWith) { effect.scale_with = scaleWith; return this; }
    public ModelEffectBuilder duration(int ticks) { effect.duration = ticks; return this; }
    public ModelEffectBuilder light(LightEmission emission) { effect.light_emission = emission; return this; }
    public ModelEffectBuilder followEntity() { effect.follow_entity = true; return this; }
    /** Vertical placement within the entity bounding box: 0 = feet, 0.5 = center, 1 = top. */
    public ModelEffectBuilder positioning(float vertical) { effect.positioning.vertical = vertical; return this; }

    // --- Static (initial) transforms ---

    /** Permanent rotation applied every frame at full progress — useful for angular spread offsets. */
    public ModelEffectBuilder initialRotate(float x, float y, float z) {
        effect.initial.add(transform("rotate", x, y, z));
        return this;
    }

    /**
     * Permanent translation applied every frame at full progress. Combines with any existing initial
     * translate (added by an earlier call) so the list holds at most one translate transform, keeping
     * its original position relative to other transforms. The existing transform is replaced rather
     * than mutated, so translates shared with copied builders are never affected.
     */
    public ModelEffectBuilder initialTranslate(float x, float y, float z) {
        for (int i = 0; i < effect.initial.size(); i++) {
            var existing = effect.initial.get(i);
            if ("translate".equals(existing.operation)) {
                effect.initial.set(i, transform("translate", existing.x + x, existing.y + y, existing.z + z));
                return this;
            }
        }
        effect.initial.add(transform("translate", x, y, z));
        return this;
    }

    /** Adds to the permanent initial translation along the X axis. See {@link #initialTranslate}. */
    public ModelEffectBuilder initialTranslateX(float x) { return initialTranslate(x, 0, 0); }

    /** Adds to the permanent initial translation along the Y axis. See {@link #initialTranslate}. */
    public ModelEffectBuilder initialTranslateY(float y) { return initialTranslate(0, y, 0); }

    /** Adds to the permanent initial translation along the Z axis. See {@link #initialTranslate}. */
    public ModelEffectBuilder initialTranslateZ(float z) { return initialTranslate(0, 0, z); }

    /**
     * Permanent scale delta applied every frame at full progress. Combines with any existing initial
     * scale delta (added by an earlier call) so the list holds at most one — apart from the internal
     * base-scale sentinel injected by {@link #scaleIn}/{@link #scaleOut}, which is left untouched so
     * the scale-from-zero detection keeps working. The existing transform is replaced rather than
     * mutated, so scales shared with copied builders are never affected. Scale deltas are summed by
     * the renderer regardless of list position, so combining is purely a tidying step.
     */
    public ModelEffectBuilder initialScale(float x, float y, float z) {
        for (int i = 0; i < effect.initial.size(); i++) {
            var existing = effect.initial.get(i);
            if ("scale".equals(existing.operation) && !isBaseScale(existing)) {
                effect.initial.set(i, transform("scale", existing.x + x, existing.y + y, existing.z + z));
                return this;
            }
        }
        effect.initial.add(transform("scale", x, y, z));
        return this;
    }

    public ModelEffectBuilder initialScale(float scale) {
        return initialScale(scale, scale, scale);
    }

    // --- Scale animations ---

    /** Grows the model from scale 0 to full over [start, end] ticks. */
    public ModelEffectBuilder scaleIn(int start, int end, Easing easing) {
        ensureBaseScale();
        effect.animations.add(anim("scale", 1, 1, 1, start, end, easing));
        return this;
    }

    /** Shrinks the model from full scale to 0 over [start, end] ticks. */
    public ModelEffectBuilder scaleOut(int start, int end, Easing easing) {
        ensureBaseScale();
        effect.animations.add(anim("scale", -1, -1, -1, start, end, easing));
        return this;
    }

    // --- Rotation / translation animations ---

    /** Animates rotation by the given per-axis degrees over [start, end] ticks. */
    public ModelEffectBuilder rotate(float xDeg, float yDeg, float zDeg,
                                     int start, int end, Easing easing) {
        effect.animations.add(anim("rotate", xDeg, yDeg, zDeg, start, end, easing));
        return this;
    }

    /** Animates translation by the given per-axis block offset over [start, end] ticks. */
    public ModelEffectBuilder translate(float x, float y, float z,
                                        int start, int end, Easing easing) {
        effect.animations.add(anim("translate", x, y, z, start, end, easing));
        return this;
    }

    // --- Composite patterns ---

    /**
     * Orbits around the Y axis at the given radius over [start, end] ticks.
     *
     * <p>Adds a Y-rotation animation followed by a constant-offset Z translation, in the order
     * required so the translation direction rotates with the orbit angle (rotate transforms the
     * coordinate frame that translate operates in).</p>
     */
    public ModelEffectBuilder orbit(float radius, float degrees, int start, int end, Easing easing) {
        effect.animations.add(anim("rotate", 0, degrees, 0, start, end, easing));
        // end == start makes progress always 1 → constant radius regardless of time.
        effect.animations.add(anim("translate", 0, 0, -radius, 0, 0, Easing.LINEAR));
        return this;
    }

    // --- Timing adjustments ---

    /**
     * Delays the most recently added animated operation by {@code ticks}, moving its start and end
     * together so its duration is preserved. No-op if no animations have been added yet. Initial
     * (static) transforms are untouched — they have no time dimension.
     */
    public ModelEffectBuilder delayLast(int ticks) {
        int last = effect.animations.size() - 1;
        if (last >= 0) {
            effect.animations.set(last, shifted(effect.animations.get(last), ticks));
        }
        return this;
    }

    /**
     * Delays every animated operation by {@code ticks}, moving each start and end together so all
     * durations are preserved. Initial (static) transforms are untouched. Note this can push
     * animations past {@link #duration}; extend the duration separately if needed.
     */
    public ModelEffectBuilder delayAll(int ticks) {
        for (int i = 0; i < effect.animations.size(); i++) {
            effect.animations.set(i, shifted(effect.animations.get(i), ticks));
        }
        return this;
    }

    // --- Build ---

    /** Returns the {@link ModelEffect} being built. */
    public ModelEffect build() { return effect; }

    /** Returns a new builder with a deep copy of the current effect. */
    public ModelEffectBuilder copy() { return deepCopy(effect); }

    // --- Collection helpers ---

    /**
     * Applies {@code configure} to a builder seeded from each effect in {@code effects},
     * returning a new list of rebuilt effects. Use this to add transforms retrospectively
     * to a list produced by a preset or another builder.
     *
     * <pre>{@code
     * var effects = Preset.orbiters(modelId, count, radius, duration, easing);
     * effects = ModelEffectBuilder.forEach(effects, b -> b
     *     .scaleIn(0, 10, ModelEffect.Easing.EASE_OUT_CUBIC)
     *     .scaleOut(duration - 10, duration, ModelEffect.Easing.EASE_IN_CUBIC));
     * }</pre>
     */
    public static List<ModelEffect> forEach(List<ModelEffect> effects, Consumer<ModelEffectBuilder> configure) {
        return effects.stream().map(e -> {
            var builder = deepCopy(e);
            configure.accept(builder);
            return builder.build();
        }).toList();
    }

    // --- Presets ---

    /**
     * Pre-configured {@link ModelEffect} patterns for common spell FX scenarios.
     *
     * <p>Each method returns a ready-to-use {@code List<ModelEffect>} or {@link ModelEffect}.
     * For customisation beyond what the preset parameters expose, use {@link ModelEffectBuilder}
     * directly.</p>
     */
    public static class Preset {

        /**
         * {@code count} models evenly distributed around the Y axis, each sweeping
         * {@code totalAngle} degrees at the given {@code radius} over {@code duration} ticks.
         * Positive angles orbit counter-clockwise, negative clockwise.
         *
         * <p>To add scale animation, pipe the result through {@link ModelEffectBuilder#forEach}:</p>
         * <pre>{@code
         * var effects = Preset.orbiters(modelId, count, radius, 360F, duration, easing);
         * effects = ModelEffectBuilder.forEach(effects, b -> b
         *     .scaleIn(0, 10, ModelEffect.Easing.EASE_OUT_CUBIC)
         *     .scaleOut(duration - 10, duration, ModelEffect.Easing.EASE_IN_CUBIC));
         * }</pre>
         */
        public static List<ModelEffect> orbiters(String modelId, int count, float radius,
                                                  float totalAngle, int duration, Easing easing) {
            return create(modelId)
                    .followEntity()
                    .duration(duration)
                    .orbit(radius, totalAngle, 0, duration, easing)
                    .spreadBuild(count);
        }

        /**
         * Applies a "spike" eruption to an existing model builder: the model rises from below ground
         * to full height, holds there, then retracts back underground. Returns the same
         * {@link ModelEffectBuilder} (not yet built) so callers can chain further transforms — rotation,
         * {@link ModelEffectBuilder#delayAll}, etc. — before {@code build()}.
         *
         * <p>Timeline (ticks): rise over [0, riseTime] → hold over [riseTime, riseTime + upTime] → sink
         * over [riseTime + upTime, 2*riseTime + upTime]. Full height is reached exactly at
         * {@code riseTime}. The model rests {@code buryDepth} blocks underground so it is hidden before
         * and after. When {@code withScale} is set, it also scales up with the rise and back down with
         * the sink, on the same windows. The rise eases out elastic (punchy, springs at the top); the
         * sink eases in elastic.</p>
         *
         * @param builder   a builder with its model and light emission already configured
         * @param riseTime  ticks to rise to full height; the retract mirrors it
         * @param upTime    ticks held at full height before retracting
         * @param withScale also scale the model in with the rise and out with the sink
         * @param buryDepth blocks the model rests underground at rest, so it stays hidden
         */
        public static ModelEffectBuilder spike(ModelEffectBuilder builder, int riseTime, int upTime,
                                                boolean withScale, float buryDepth) {
            int sinkStart = riseTime + upTime;
            int end = sinkStart + riseTime;
            builder.duration(end)
                    .initialTranslate(0, -buryDepth, 0)
                    .translate(0, buryDepth, 0, 0, riseTime, Easing.EASE_OUT_ELASTIC)
                    .translate(0, -buryDepth, 0, sinkStart, end, Easing.EASE_IN_ELASTIC);
            if (withScale) {
                builder.scaleIn(0, riseTime, Easing.EASE_OUT_ELASTIC)
                        .scaleOut(sinkStart, end, Easing.EASE_IN_ELASTIC);
            }
            return builder;
        }
    }

    // --- Internal helpers ---

    private static ModelEffectBuilder deepCopy(ModelEffect source) {
        var copy = new ModelEffect();
        copy.model_id = source.model_id;
        copy.light_emission = source.light_emission;
        copy.scale = source.scale;
        copy.duration = source.duration;
        copy.positioning = new ModelEffect.Positioning();
        copy.positioning.vertical = source.positioning != null ? source.positioning.vertical : 0.5F;
        copy.follow_entity = source.follow_entity;
        copy.initial = new ArrayList<>(source.initial);
        copy.animations = new ArrayList<>(source.animations);
        return new ModelEffectBuilder(copy);
    }

    /**
     * Builds {@code count} copies with evenly-distributed Y-axis angular offsets
     * ({@code 360° / count} apart).
     */
    private List<ModelEffect> spreadBuild(int count) {
        var results = new ArrayList<ModelEffect>(count);
        float step = 360F / count;
        for (int i = 0; i < count; i++) {
            var c = copy();
            if (i > 0) c.initialRotate(0, step * i, 0);
            results.add(c.build());
        }
        return results;
    }

    /**
     * Injects a base scale delta of −1 into the front of the initial list exactly once.
     * Effective scale = effect.scale × (1 + (−1 + animatedDelta)): starts at 0, rises with
     * scaleIn (+1), falls back to 0 with scaleOut (−1).
     */
    private void ensureBaseScale() {
        if (baseScaleInjected) return;
        effect.initial.add(0, transform("scale", -1, -1, -1));
        baseScaleInjected = true;
    }

    /** The scale-from-zero sentinel that {@link #ensureBaseScale} injects: a scale delta of −1 on
     *  every axis. Combining other scale deltas into it would break {@link #baseScaleInjected} detection. */
    private static boolean isBaseScale(ModelEffect.Transform t) {
        return "scale".equals(t.operation) && t.x == -1 && t.y == -1 && t.z == -1;
    }

    private static ModelEffect.Transform transform(String op, float x, float y, float z) {
        var t = new ModelEffect.Transform();
        t.operation = op; t.x = x; t.y = y; t.z = z;
        return t;
    }

    private static ModelEffect.Animation anim(String op, float x, float y, float z,
                                               int start, int end, Easing easing) {
        var a = new ModelEffect.Animation();
        a.operation = op; a.x = x; a.y = y; a.z = z;
        a.start = start; a.end = end; a.easing = easing;
        return a;
    }

    /** A copy of {@code a} with its window shifted later by {@code ticks}. Replacing rather than
     *  mutating keeps animations shared with copied builders ({@link #deepCopy}) unaffected. */
    private static ModelEffect.Animation shifted(ModelEffect.Animation a, int ticks) {
        return anim(a.operation, a.x, a.y, a.z, a.start + ticks, a.end + ticks, a.easing);
    }
}
