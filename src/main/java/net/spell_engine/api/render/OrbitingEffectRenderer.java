package net.spell_engine.api.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.spell_engine.api.effect.CustomModelStatusEffect;

import java.util.List;

public class OrbitingEffectRenderer implements CustomModelStatusEffect.Renderer {
    public record Model(RenderLayer layer, Identifier modelId) { }
    private List<Model> models;
    private float scale;
    private float horizontalOffset;

    public OrbitingEffectRenderer(List<Model> models, float scale, float horizontalOffset) {
        this.models = models;
        this.scale = scale;
        this.horizontalOffset = horizontalOffset;
    }

    @Override
    public void renderEffect(int amplifier, LivingEntity livingEntity, float delta, MatrixStack matrixStack, VertexConsumerProvider vertexConsumers, int light) {
        matrixStack.push();
        var time = livingEntity.getWorld().getTime() + delta;

        var initialAngle = time * 2.25F - 45.0F;
        var horizontalOffset = this.horizontalOffset * livingEntity.getScaleFactor();
        var verticalOffset = livingEntity.getHeight() / 2F;
        var itemRenderer = MinecraftClient.getInstance().getItemRenderer();

        var stacks = amplifier + 1;
        var turnAngle = 360F / stacks;
        for (int i = 0; i < stacks; i++) {
            var angle = initialAngle + turnAngle * i;
            renderModel(matrixStack, scale, verticalOffset, horizontalOffset, angle, itemRenderer, vertexConsumers, light, livingEntity);
        }

        matrixStack.pop();
    }

    private void renderModel(MatrixStack matrixStack, float scale, float verticalOffset, float horizontalOffset, float rotation,
                               ItemRenderer itemRenderer, VertexConsumerProvider vertexConsumers, int light, LivingEntity livingEntity) {
        matrixStack.push();
        matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotation));
        matrixStack.translate(0, verticalOffset, -horizontalOffset);
        matrixStack.scale(scale, scale, scale);

        for(var model: models) {
            matrixStack.push();
            CustomModels.render(model.layer, itemRenderer, model.modelId,
                    matrixStack, vertexConsumers, light, livingEntity.getId());
            matrixStack.pop();
        }

        matrixStack.pop();
    }
}
