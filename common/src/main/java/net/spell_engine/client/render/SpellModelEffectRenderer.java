package net.spell_engine.client.render;

import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.spell_engine.entity.SpellModelEffect;
import org.jetbrains.annotations.Nullable;

public class SpellModelEffectRenderer<T extends SpellModelEffect> extends EntityRenderer<T, SpellModelEffectRenderer.State> {
    public static class State extends EntityRenderState {
        @Nullable public SpellModelEffect entity;
        public float tickDelta;
    }

    public SpellModelEffectRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void updateRenderState(T entity, State state, float tickDelta) {
        super.updateRenderState(entity, state, tickDelta);
        state.entity = entity;
        state.tickDelta = tickDelta;
    }

    @Override
    public void render(State state, MatrixStack matrixStack, OrderedRenderCommandQueue queue, CameraRenderState cameraState) {
        super.render(state, matrixStack, queue, cameraState);
        var entity = state.entity;
        if (entity == null) {
            return;
        }
        var effect = entity.getModelEffect();
        if (effect == null || effect.model_id == null || effect.model_id.isEmpty()) {
            return;
        }
        float age = entity.age + state.tickDelta;
        ModelEffectOperations.renderEffect(effect, age, matrixStack, queue, state.light, entity.getId());
    }
}
