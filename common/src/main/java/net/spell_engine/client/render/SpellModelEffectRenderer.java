package net.spell_engine.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.spell_engine.entity.SpellModelEffect;
import org.jetbrains.annotations.Nullable;

public class SpellModelEffectRenderer<T extends SpellModelEffect> extends EntityRenderer<T, SpellModelEffectRenderer.State> {
    public static class State extends EntityRenderState {
        @Nullable public SpellModelEffect entity;
        public float tickDelta;
    }

    public SpellModelEffectRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(T entity, State state, float tickDelta) {
        super.extractRenderState(entity, state, tickDelta);
        state.entity = entity;
        state.tickDelta = tickDelta;
    }

    @Override
    public void submit(State state, PoseStack matrixStack, SubmitNodeCollector queue, CameraRenderState cameraState) {
        super.submit(state, matrixStack, queue, cameraState);
        var entity = state.entity;
        if (entity == null) {
            return;
        }
        var effect = entity.getModelEffect();
        if (effect == null || effect.model_id == null || effect.model_id.isEmpty()) {
            return;
        }
        float age = entity.tickCount + state.tickDelta;
        ModelEffectOperations.renderEffect(effect, age, matrixStack, queue, state.lightCoords, entity.getId());
    }
}
