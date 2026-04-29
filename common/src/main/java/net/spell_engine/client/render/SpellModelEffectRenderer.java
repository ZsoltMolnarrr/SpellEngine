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

        // Scale transforms are accumulated additively and applied as a single call at the end,
        // because MatrixStack.scale() composes multiplicatively — chaining scale(0)*scale(s) = 0 forever.
        // All other operations (translate, rotate) go through the MatrixStack directly.
        float sx = 0, sy = 0, sz = 0;

        // Apply initial transforms (at full effect, progress=1)
        for (var transform : effect.initial) {
            if ("scale".equals(transform.operation)) {
                sx += transform.x; sy += transform.y; sz += transform.z;
            } else {
                var handler = ModelEffectOperations.get(transform.operation);
                if (handler != null) handler.apply(matrixStack, 1F, transform);
            }
        }

        // Apply animated transforms
        float age = entity.age + tickDelta;
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

        // Apply base scale combined with accumulated scale deltas as one call
        matrixStack.scale(effect.scale * (1 + sx), effect.scale * (1 + sy), effect.scale * (1 + sz));

        // Render model
        var modelId = Identifier.of(effect.model_id);
        var layer = SpellModelHelper.LAYERS.get(effect.light_emission);
        CustomModels.render(layer, itemRenderer, modelId, matrixStack, vertexConsumers, light, entity.getId());

        matrixStack.pop();
    }
}
