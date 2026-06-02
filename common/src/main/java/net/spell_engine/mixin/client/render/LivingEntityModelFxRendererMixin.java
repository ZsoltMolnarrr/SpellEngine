package net.spell_engine.mixin.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.spell_engine.api.spell.fx.ModelEffectAttachment;
import net.spell_engine.client.render.ModelEffectOperations;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityModelFxRendererMixin {

    @Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("TAIL"))
    private void render_TAIL_SpellEngine_ModelFx(LivingEntity entity, float yaw, float delta,
            MatrixStack matrices, VertexConsumerProvider vtc, int light, CallbackInfo ci) {
        var client = MinecraftClient.getInstance();
        var camera = client.gameRenderer.getCamera();
        if (camera == null) return;
        if (entity == camera.getFocusedEntity() && !camera.isThirdPerson()) return;

        var attached = ModelEffectAttachment.of(entity);
        if (attached.isEmpty()) return;

        var itemRenderer = client.getItemRenderer();
        for (var entry : attached) {
            var effect = entry.effect();
            if (effect == null || effect.model_id == null || effect.model_id.isEmpty()) continue;
            float time = (float) ((entity.getWorld().getTime() + delta) - entry.appliedAtWorldTime()) % effect.duration;
            matrices.push();
            matrices.translate(0, effect.positioning.vertical * entity.getHeight(), 0);
            ModelEffectOperations.renderEffect(effect, time, matrices, itemRenderer, vtc, light, entity.getId());
            matrices.pop();
        }
    }
}
