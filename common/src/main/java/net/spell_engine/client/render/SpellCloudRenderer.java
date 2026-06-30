package net.spell_engine.client.render;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.spell_engine.api.render.CustomModels;
import net.spell_engine.api.spell.Spell;
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
        var clientData = data.client_data;
        if (!clientData.model_fx.isEmpty()) {
            renderModelFx(entity, clientData, tickDelta, matrixStack, vertexConsumers, light); // new path
        } else {
            renderLegacyModel(entity, clientData, tickDelta, matrixStack, vertexConsumers, light); // legacy path
        }
    }

    /// New multi-model path: each model animated through the modelFX system, under the shared
    /// cloud-root transform. Animation time is the cloud's age (lines up with its lifecycle phases).
    private void renderModelFx(T entity, Spell.Delivery.Cloud.ClientData clientData, float tickDelta,
                               MatrixStack matrixStack, VertexConsumerProvider vertexConsumers, int light) {
        matrixStack.push();
        matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-1F * entity.getYaw() + 180F));
        matrixStack.translate(0, 0.5, 0); // Compensate for translate within CustomModels.render

        float age = entity.age + tickDelta;
        for (var effect : clientData.model_fx) {
            if (effect == null || effect.model_id == null || effect.model_id.isEmpty()) {
                continue;
            }
            matrixStack.push();
            if (effect.positioning != null && effect.positioning.vertical != 0) {
                matrixStack.translate(0, effect.positioning.vertical * entity.getHeight(), 0);
            }
            ModelEffectOperations.renderEffect(effect, age, matrixStack, itemRenderer, vertexConsumers, light, entity.getId());
            matrixStack.pop();
        }

        matrixStack.pop();
    }

    @SuppressWarnings("removal") // legacy single-model path; delete together with ProjectileModel
    private void renderLegacyModel(T entity, Spell.Delivery.Cloud.ClientData clientData, float tickDelta,
                                   MatrixStack matrixStack, VertexConsumerProvider vertexConsumers, int light) {
        var renderData = clientData.model;
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
