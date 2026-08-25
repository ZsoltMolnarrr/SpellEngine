package net.spell_engine.client.render;

import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.entity.SpellCloud;
import org.jetbrains.annotations.Nullable;

public class SpellCloudRenderer<T extends SpellCloud> extends EntityRenderer<T, SpellCloudRenderer.State> {
    public static class State extends EntityRenderState {
        @Nullable public SpellCloud cloud;
        public float tickDelta;
    }

    public SpellCloudRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void updateRenderState(T entity, State state, float tickDelta) {
        super.updateRenderState(entity, state, tickDelta);
        state.cloud = entity;
        state.tickDelta = tickDelta;
    }

    @Override
    public void render(State state, MatrixStack matrixStack, OrderedRenderCommandQueue queue, CameraRenderState cameraState) {
        super.render(state, matrixStack, queue, cameraState);
        var entity = state.cloud;
        if (entity == null) {
            return;
        }
        var data = entity.getCloudData();
        if (data == null) {
            return;
        }
        var clientData = data.client_data;
        if (!clientData.model_fx.isEmpty()) {
            renderModelFx(entity, clientData, state.tickDelta, matrixStack, queue, state.light);
        }
    }

    /// Each model animated through the modelFX system, under the shared cloud-root transform. Animation time is the cloud's age (lines up with its lifecycle phases).
    private void renderModelFx(SpellCloud entity, Spell.Delivery.Cloud.ClientData clientData, float tickDelta,
                               MatrixStack matrixStack, OrderedRenderCommandQueue queue, int light) {
        matrixStack.push();
        matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-1F * entity.getYaw() + 180F));
        // Grow the model with the cloud's radius, applied about the ground origin (before the 0.5 lift)
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
            ModelEffectOperations.renderEffect(effect, age, matrixStack, queue, light, entity.getId());
            matrixStack.pop();
        }

        matrixStack.pop();
    }
}
