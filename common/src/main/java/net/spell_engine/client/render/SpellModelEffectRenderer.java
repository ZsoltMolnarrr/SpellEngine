package net.spell_engine.client.render;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
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

        float age = entity.age + tickDelta;
        ModelEffectOperations.renderEffect(effect, age, matrixStack, itemRenderer, vertexConsumers, light, entity.getId());
    }
}
