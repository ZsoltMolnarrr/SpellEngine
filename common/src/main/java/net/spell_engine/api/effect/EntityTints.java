package net.spell_engine.api.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.spell_engine.internals.SpellEngineAttachments;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/// Status-effect-driven ARGB tinting of a living entity's whole rendered appearance:
/// body model, worn armor and extra render passes. Register a tint for a status effect,
/// and every entity carrying that effect gets tinted for all players tracking it.
public final class EntityTints {
    public static final int NEUTRAL = 0xFFFFFFFF;

    /// Tint contributed by a single status effect. Free to derive its color from any state of
    /// the affected entity or the effect instance (amplifier, remaining duration, entity type…).
    /// Evaluated server side, whenever the entity's effect set changes.
    @FunctionalInterface
    public interface Tint {
        /// ARGB this effect contributes, `NEUTRAL` for none.
        int argb(LivingEntity entity, MobEffectInstance instance);

        /// Constant color: the full `argb` at any stack count.
        static Tint flat(int argb) {
            return (entity, instance) -> argb;
        }

        /// Stack-strengthened color: each stack (amplifier + 1) pulls the applied color further
        /// from neutral towards `argb` by `strengthPerStack`, capped at the full value — so a
        /// stacked effect tints stronger than a single application of it.
        static Tint scaling(int argb, float strengthPerStack) {
            return (entity, instance) -> scaled(argb, strengthPerStack * (instance.getAmplifier() + 1));
        }
    }

    /// `argb` weakened towards neutral: each channel interpolated from neutral towards its full
    /// value by `strength` (0 = neutral, 1+ = full). Building block for dynamic tints.
    public static int scaled(int argb, float strength) {
        if (strength >= 1F) { return argb; }
        if (strength <= 0F) { return NEUTRAL; }
        int alpha = scaledChannel(argb >>> 24, strength);
        int red = scaledChannel((argb >> 16) & 0xFF, strength);
        int green = scaledChannel((argb >> 8) & 0xFF, strength);
        int blue = scaledChannel(argb & 0xFF, strength);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static int scaledChannel(int target, float strength) {
        return Math.round(0xFF + (target - 0xFF) * strength);
    }

    private static final Map<MobEffect, Tint> tints = new HashMap<>();

    public static void register(MobEffect statusEffect, Tint tint) {
        tints.put(statusEffect, tint);
    }

    public static void register(MobEffect statusEffect, int argb) {
        register(statusEffect, Tint.flat(argb));
    }

    public static void register(MobEffect statusEffect, int argb, float strengthPerStack) {
        register(statusEffect, Tint.scaling(argb, strengthPerStack));
    }

    @Nullable
    public static Tint tintOf(MobEffect statusEffect) {
        return tints.get(statusEffect);
    }

    /// The combined tint of all registered tinting effects active on `entity`, `NEUTRAL` if none
    /// reach it. Runs server side; the result travels to clients as a synced attachment (see currentTint).
    /// <p>
    /// Blend rule: componentwise multiply (each channel as 0–1, product across all active tints).
    /// Neutral-identity, commutative, associative — order of effects can't matter — and alphas
    /// compose naturally (two half-transparencies stack). Two saturated tints multiply dark.
    public static int resolve(LivingEntity entity) {
        if (tints.isEmpty()) {
            return NEUTRAL;
        }
        int result = NEUTRAL;
        for (var instance : entity.getActiveEffects()) {
            var tint = tints.get(instance.getEffect().value());
            if (tint != null) {
                result = multiply(result, tint.argb(entity, instance));
            }
        }
        return result;
    }

    public static int multiply(int a, int b) {
        if (a == NEUTRAL) { return b; }
        if (b == NEUTRAL) { return a; }
        int alpha = (a >>> 24) * (b >>> 24) / 255;
        int red = ((a >> 16) & 0xFF) * ((b >> 16) & 0xFF) / 255;
        int green = ((a >> 8) & 0xFF) * ((b >> 8) & 0xFF) / 255;
        int blue = (a & 0xFF) * (b & 0xFF) / 255;
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    /// The entity's current blended tint, synced to all tracking clients as an entity attachment
    /// (`SpellEngineAttachments.TINT_ARGB`, written whenever the server re-resolves the effect set).
    /// Client side, `LivingEntityRenderer.updateRenderState` copies it onto the entity's render state
    /// (`EntityRenderStateExtension.spellEngine_getTint`), which is what the render pass reads.
    public static int currentTint(LivingEntity entity) {
        return SpellEngineAttachments.TINT_ARGB.get(entity);
    }
}
