package net.spell_engine.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.entity.SpellCloud;
import org.jetbrains.annotations.Nullable;

public class SpellCloudRenderer<T extends SpellCloud> extends EntityRenderer<T, SpellCloudRenderer.State> {
    public static class State extends EntityRenderState {
        @Nullable public SpellCloud cloud;
        public float tickDelta;
    }

    public SpellCloudRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(T entity, State state, float tickDelta) {
        super.extractRenderState(entity, state, tickDelta);
        state.cloud = entity;
        state.tickDelta = tickDelta;
    }

    @Override
    public void submit(State state, PoseStack matrixStack, SubmitNodeCollector queue, CameraRenderState cameraState) {
        super.submit(state, matrixStack, queue, cameraState);
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
            renderModelFx(entity, clientData, state.tickDelta, matrixStack, queue, state.lightCoords);
        }
    }

    /// Each model animated through the modelFX system, under the shared cloud-root transform. Animation time is the cloud's age (lines up with its lifecycle phases).
    private void renderModelFx(SpellCloud entity, Spell.Delivery.Cloud.ClientData clientData, float tickDelta,
                               PoseStack matrixStack, SubmitNodeCollector queue, int light) {
        matrixStack.pushPose();
        matrixStack.mulPose(Axis.YP.rotationDegrees(-1F * entity.getYRot() + 180F));
        // Grow the model with the cloud's radius, applied about the ground origin (before the 0.5 lift)
        float renderScale = entity.getRenderScale(tickDelta);
        if (renderScale != 1F) {
            matrixStack.scale(renderScale, renderScale, renderScale);
        }
        matrixStack.translate(0, 0.5, 0); // Compensate for translate within CustomModels.render

        float age = entity.tickCount + tickDelta;
        for (var effect : clientData.model_fx) {
            if (effect == null || effect.model_id == null || effect.model_id.isEmpty()) {
                continue;
            }
            matrixStack.pushPose();
            if (effect.positioning != null && effect.positioning.vertical != 0) {
                matrixStack.translate(0, effect.positioning.vertical * entity.getBbHeight(), 0);
            }
            ModelEffectOperations.renderEffect(effect, age, matrixStack, queue, light, entity.getId());
            matrixStack.popPose();
        }

        matrixStack.popPose();
    }
}
