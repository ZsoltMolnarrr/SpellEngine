package net.spell_engine.mixin.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.spell_engine.api.spell.fx.ModelEffectAttachment;
import net.spell_engine.client.render.ModelEffectOperations;
import net.spell_engine.mixin.client.render.state.EntityRenderStateExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityModelFxRendererMixin {

    @Inject(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V", at = @At("TAIL"))
    private void render_TAIL_SpellEngine_ModelFx(LivingEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraState, CallbackInfo ci) {
        if (!(((EntityRenderStateExtension) state).spellEngine_getEntity() instanceof LivingEntity entity)) return;
        var delta = ((EntityRenderStateExtension) state).spellEngine_getTickDelta();
        var client = MinecraftClient.getInstance();
        var camera = client.gameRenderer.getCamera();
        if (camera == null) return;
        if (entity == camera.getFocusedEntity() && !camera.isThirdPerson()) return;

        var attached = ModelEffectAttachment.of(entity);
        if (attached.isEmpty()) return;

        for (var entry : attached) {
            var effect = entry.effect();
            if (effect == null || effect.model_id == null || effect.model_id.isEmpty()) continue;
            float time = (float) ((entity.getEntityWorld().getTime() + delta) - entry.appliedAtWorldTime()) % effect.duration;
            matrices.push();
            matrices.translate(0, effect.positioning.vertical * entity.getHeight(), 0);
            ModelEffectOperations.renderEffect(effect, time, matrices, queue, state.light, entity.getId());
            matrices.pop();
        }
    }
}
