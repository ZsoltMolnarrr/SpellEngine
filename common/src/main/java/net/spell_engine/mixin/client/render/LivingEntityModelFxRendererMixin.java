package net.spell_engine.mixin.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.spell_engine.api.spell.fx.ModelEffectAttachment;
import net.spell_engine.client.render.ModelEffectOperations;
import net.spell_engine.client.render.extension.EntityRenderStateExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityModelFxRendererMixin {

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("TAIL"))
    private void render_TAIL_SpellEngine_ModelFx(LivingEntityRenderState state, PoseStack matrices, SubmitNodeCollector queue, CameraRenderState cameraState, CallbackInfo ci) {
        if (!(((EntityRenderStateExtension) state).spellEngine_getEntity() instanceof LivingEntity entity)) return;
        var delta = ((EntityRenderStateExtension) state).spellEngine_getTickDelta();
        var client = Minecraft.getInstance();
        var camera = client.gameRenderer.getMainCamera();
        if (camera == null) return;
        if (entity == camera.entity() && !camera.isDetached()) return;

        var attached = ModelEffectAttachment.of(entity);
        if (attached.isEmpty()) return;

        for (var entry : attached) {
            var effect = entry.effect();
            if (effect == null || effect.model_id == null || effect.model_id.isEmpty()) continue;
            float time = (float) ((entity.level().getGameTime() + delta) - entry.appliedAtWorldTime()) % effect.duration;
            matrices.pushPose();
            matrices.translate(0, effect.positioning.vertical * entity.getBbHeight(), 0);
            ModelEffectOperations.renderEffect(effect, time, matrices, queue, state.lightCoords, entity.getId());
            matrices.popPose();
        }
    }
}
