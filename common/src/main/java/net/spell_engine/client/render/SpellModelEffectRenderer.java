package net.spell_engine.client.render;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.spell_engine.api.render.CustomModels;
import net.spell_engine.api.spell.fx.EasingHelper;
import net.spell_engine.entity.SpellModelEffect;

public class SpellModelEffectRenderer<T extends SpellModelEffect> extends EntityRenderer<T> {
    private final ItemRenderer itemRenderer;

    public SpellModelEffectRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public Identifier getTexture(T entity) {
        return null;
    }

    @Override
    public void render(T entity, float yaw, float tickDelta, MatrixStack matrixStack, VertexConsumerProvider vertexConsumers, int light) {
        super.render(entity, yaw, tickDelta, matrixStack, vertexConsumers, light);

        var effect = entity.getModelEffect();
        if (effect == null || effect.model_id == null || effect.model_id.isEmpty()) {
            return;
        }

        matrixStack.push();

        // Apply base scale
        matrixStack.scale(effect.scale, effect.scale, effect.scale);

        // Apply initial transforms (at full effect, progress=1)
        for (var transform : effect.initial) {
            var handler = ModelEffectOperations.get(transform.operation);
            if (handler != null) {
                handler.apply(matrixStack, 1F, transform);
            }
        }

        // Apply animated transforms
        float age = entity.age + tickDelta;
        for (var anim : effect.animations) {
            if (age < anim.start) continue;
            float t = (anim.end <= anim.start) ? 1F
                    : Math.clamp((age - anim.start) / (float)(anim.end - anim.start), 0F, 1F);
            float progress = EasingHelper.apply(anim.easing, t);
            var handler = ModelEffectOperations.get(anim.operation);
            if (handler != null) {
                handler.apply(matrixStack, progress, anim);
            }
        }

        // Render model
        var modelId = Identifier.of(effect.model_id);
        var layer = SpellModelHelper.LAYERS.get(effect.light_emission);
        CustomModels.render(layer, itemRenderer, modelId, matrixStack, vertexConsumers, light, entity.getId());

        matrixStack.pop();
    }
}
