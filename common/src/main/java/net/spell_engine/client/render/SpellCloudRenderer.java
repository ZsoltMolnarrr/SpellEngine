package net.spell_engine.client.render;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.spell_engine.api.render.CustomModels;
import net.spell_engine.entity.SpellCloud;

public class SpellCloudRenderer<T extends SpellCloud> extends EntityRenderer<T> {
    // Item renderer
    private final ItemRenderer itemRenderer;
    public SpellCloudRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public Identifier getTexture(T entity) {
        return null;
    }
    
    public void render(T entity, float yaw, float tickDelta, MatrixStack matrixStack, VertexConsumerProvider vertexConsumers, int light) {
        super.render(entity, yaw, tickDelta, matrixStack, vertexConsumers, light);

        var data = entity.getCloudData();
        if (data == null) {
            return;
        }
        var renderData = data.client_data.model;
        if (renderData == null) {
            return;
        }

        matrixStack.push();
        matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-1F * entity.getYaw() + 180F));
        matrixStack.translate(0, 0.5, 0); // Compensate for translate within CustomModels.render

        var time = entity.getWorld().getTime();
        var absoluteTime = (float)time + tickDelta;
        matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(absoluteTime * renderData.rotate_degrees_per_tick));
        matrixStack.scale(renderData.scale, renderData.scale, renderData.scale);
        if (renderData.model_id != null && !renderData.model_id.isEmpty()) {
            var modelId = Identifier.of(renderData.model_id);
            CustomModels.render(SpellModelHelper.LAYERS.get(renderData.light_emission), itemRenderer, modelId, matrixStack, vertexConsumers, light, entity.getId());
        }

        matrixStack.pop();
    }
}
