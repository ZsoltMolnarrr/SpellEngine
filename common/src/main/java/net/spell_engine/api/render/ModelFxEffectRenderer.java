package net.spell_engine.api.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.RotationAxis;
import net.spell_engine.api.effect.CustomModelStatusEffect;
import net.spell_engine.api.spell.fx.ModelEffect;
import net.spell_engine.client.render.ModelEffectOperations;

import java.util.List;

/// Renders a list of {@link ModelEffect} animations directly on a living entity as a status effect visual,
/// without spawning a world entity. Animations loop or play once based on {@link Playback}.
public class ModelFxEffectRenderer implements CustomModelStatusEffect.Renderer {

    /// Controls whether the animation repeats for as long as the effect is active, or plays through once and holds.
    public enum Playback {
        /// Animation cycles continuously, repeating every {@code ModelEffect.duration} ticks.
        LOOP,
        /// Animation plays once from the moment the effect was applied and then holds at its final state.
        ONCE
    }

    /// The entity dimension used as the basis for entity-size-based scaling.
    public enum SizeAxis { WIDTH, HEIGHT }

    /// Scales all rendered effects uniformly based on the size of the affected entity.
    /// Useful for effects that should fit the entity's body (e.g. a frozen aura around a giant).
    ///
    /// @param axis      The entity dimension used as input — {@link SizeAxis#WIDTH} or {@link SizeAxis#HEIGHT}.
    /// @param baseline  The reference size at which scale = 1.0 (e.g. {@code 1.8} for a player-height baseline).
    /// @param min       Minimum allowed scale factor, preventing the effect from becoming too small on tiny entities.
    /// @param max       Maximum allowed scale factor, preventing the effect from becoming too large on huge entities.
    /// @param exponent  Curve applied to the size ratio before clamping. Controls how aggressively scale grows
    ///                  with entity size. Use a value less than 1 for a compressed (perceptually natural) curve:
    ///                  <pre>
    ///                  Entity height | Linear (exp=1) | Sqrt (exp=0.5)
    ///                  1.8 (player)  |      1.0×      |      1.0×
    ///                  4.0           |      2.2×      |      1.5×
    ///                  9.0 (giant)   |      5.0×      |      2.2×
    ///                  </pre>
    public record EntityScaling(SizeAxis axis, float baseline, float min, float max, float exponent) {
        /// Convenience constructor using the default exponent of {@code 0.5} (square root — perceptually natural curve).
        public EntityScaling(SizeAxis axis, float baseline, float min, float max) {
            this(axis, baseline, min, max, 0.5F);
        }
        /// Computes the scale factor for the given entity: {@code clamp(pow(size / baseline, exponent), min, max)}.
        public float scaleFor(LivingEntity entity) {
            float size = (axis == SizeAxis.WIDTH) ? entity.getWidth() : entity.getHeight();
            return Math.clamp((float) Math.pow(size / baseline, exponent), min, max);
        }
    }

    /// The model effects to render. Each entry is rendered independently with its own transforms and timing.
    private final List<ModelEffect> effects;
    /// Controls animation timing — looping or one-shot. Default: {@link Playback#LOOP}.
    private Playback playback;
    /// When true, the rendered models are rotated to match the entity's horizontal look direction (yaw).
    private boolean followYaw;
    /// When set, all effects are uniformly scaled based on the entity's size. Default: null (no entity scaling).
    private EntityScaling entityScaling;

    public ModelFxEffectRenderer(List<ModelEffect> effects) {
        this(effects, Playback.LOOP, false);
    }

    public ModelFxEffectRenderer(List<ModelEffect> effects, Playback playback) {
        this(effects, playback, false);
    }

    public ModelFxEffectRenderer(List<ModelEffect> effects, Playback playback, boolean followYaw) {
        this.effects = effects;
        this.playback = playback;
        this.followYaw = followYaw;
    }

    public ModelFxEffectRenderer playback(Playback playback) {
        this.playback = playback;
        return this;
    }

    public ModelFxEffectRenderer followYaw(boolean followYaw) {
        this.followYaw = followYaw;
        return this;
    }

    public ModelFxEffectRenderer entityScaling(EntityScaling entityScaling) {
        this.entityScaling = entityScaling;
        return this;
    }

    public ModelFxEffectRenderer entityScaling(SizeAxis axis, float baseline) {
        return entityScaling(new EntityScaling(axis, baseline, 0.5F, 3.0F));
    }

    @Override
    public void renderEffect(int amplifier, LivingEntity livingEntity, float delta, MatrixStack matrixStack, VertexConsumerProvider vertexConsumers, int light) {
        // Legacy
    }

    @Override
    public void renderEffect(int appliedAtAge, int amplifier, LivingEntity livingEntity, float delta, MatrixStack matrixStack,
                 VertexConsumerProvider vertexConsumers, int light) {
        var itemRenderer = MinecraftClient.getInstance().getItemRenderer();
        float rawTime = livingEntity.age - appliedAtAge + delta;

        if (followYaw) {
            float yaw = livingEntity.getYaw(delta);
            matrixStack.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees(yaw));
        }

        if (entityScaling != null) {
            float s = entityScaling.scaleFor(livingEntity);
            matrixStack.scale(s, s, s);
        }

        for (var effect : effects) {
            if (effect.model_id == null || effect.model_id.isEmpty()) continue;

            float age;
            if (playback == Playback.LOOP && effect.duration > 0) {
                age = rawTime % effect.duration;
            } else {
                age = rawTime;
            }

            ModelEffectOperations.renderEffect(effect, age, matrixStack, itemRenderer, vertexConsumers, light, livingEntity.getId());
        }
    }
}
