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

public class ModelFxEffectRenderer implements CustomModelStatusEffect.Renderer {
    public enum Playback { LOOP, ONCE }

    private final List<ModelEffect> effects;
    private Playback playback;
    private boolean followYaw;

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
