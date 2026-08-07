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
            renderModelFx(entity, clientData, tickDelta, matrixStack, vertexConsumers, light);
        }
    }

    /// Each model animated through the modelFX system, under the shared cloud-root transform. Animation time is the cloud's age (lines up with its lifecycle phases).
    private void renderModelFx(T entity, Spell.Delivery.Cloud.ClientData clientData, float tickDelta,
                               MatrixStack matrixStack, VertexConsumerProvider vertexConsumers, int light) {
        matrixStack.push();
        matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-1F * entity.getYaw() + 180F));
        // Grow the model with the cloud's radius. Applied about the ground origin (before the 0.5 lift)
        // so the model scales up from where it sits. `getRenderScale` interpolates within the tick, so a
        // discrete growth step doesn't pop; it's 1.0 for a non-growing cloud, leaving existing clouds
        // untouched.
        float renderScale = entity.getRenderScale(tickDelta);
        if (renderScale != 1F) {
            matrixStack.scale(renderScale, renderScale, renderScale);
        }
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
}
