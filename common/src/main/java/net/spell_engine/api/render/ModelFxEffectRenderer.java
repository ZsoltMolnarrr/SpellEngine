package net.spell_engine.api.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.spell_engine.api.effect.CustomModelStatusEffect;
import net.spell_engine.api.spell.fx.EasingHelper;
import net.spell_engine.api.spell.fx.ModelEffect;
import net.spell_engine.client.render.ModelEffectOperations;
import net.spell_engine.client.render.SpellModelHelper;

import java.util.List;

public class ModelFxEffectRenderer implements CustomModelStatusEffect.Renderer {
    private final List<ModelEffect> effects;

    public ModelFxEffectRenderer(List<ModelEffect> effects) {
        this.effects = effects;
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

        for (var effect : effects) {
            if (effect.model_id == null || effect.model_id.isEmpty()) continue;

            // Loop animation over duration
            float age = (effect.duration > 0) ? (rawTime % effect.duration) : rawTime;

            matrixStack.push();

            // Scale deltas accumulated additively (same pattern as SpellModelEffectRenderer)
            float sx = 0, sy = 0, sz = 0;

            // Initial transforms at full effect (progress = 1)
            for (var transform : effect.initial) {
                if ("scale".equals(transform.operation)) {
                    sx += transform.x; sy += transform.y; sz += transform.z;
                } else {
                    var handler = ModelEffectOperations.get(transform.operation);
                    if (handler != null) handler.apply(matrixStack, 1F, transform);
                }
            }

            // Animated transforms
            for (var anim : effect.animations) {
                if (age < anim.start) continue;
                float t = (anim.end <= anim.start) ? 1F
                        : Math.clamp((age - anim.start) / (float)(anim.end - anim.start), 0F, 1F);
                float progress = EasingHelper.apply(anim.easing, t);
                if ("scale".equals(anim.operation)) {
                    sx += progress * anim.x; sy += progress * anim.y; sz += progress * anim.z;
                } else {
                    var handler = ModelEffectOperations.get(anim.operation);
                    if (handler != null) handler.apply(matrixStack, progress, anim);
                }
            }

            // Apply base scale + accumulated deltas as single call
            matrixStack.scale(effect.scale * (1 + sx), effect.scale * (1 + sy), effect.scale * (1 + sz));

            var modelId = Identifier.of(effect.model_id);
            var layer = SpellModelHelper.LAYERS.get(effect.light_emission);
            CustomModels.render(layer, itemRenderer, modelId, matrixStack, vertexConsumers, light, livingEntity.getId());

            matrixStack.pop();
        }
    }
}
