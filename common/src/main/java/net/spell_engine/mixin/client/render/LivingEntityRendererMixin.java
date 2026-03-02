package net.spell_engine.mixin.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.RotationAxis;
import net.spell_engine.api.effect.CustomModelStatusEffect;
import net.spell_engine.api.effect.Synchronized;
import net.spell_engine.internals.casting.SpellCasterEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {

    @Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("HEAD"))
    private void render_HEAD_SpellEngine(LivingEntity livingEntity, float f, float delta, MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int light, CallbackInfo ci) {
        if (livingEntity instanceof SpellCasterEntity caster) {
            var process = caster.getSpellCastProcess();
            if (process != null) {
                var spell = process.spell().value();
                if (spell.active != null && spell.active.cast != null && spell.active.cast.animation_spin != 0) {
                    var ticks = process.spellCastTicksSoFar(livingEntity.getWorld().getTime());
                    var spin = spell.active.cast.animation_spin;
                    var turn = spin / (process.channelInterval(livingEntity) / 20F);
                    var degress = turn * ticks + delta * turn;
                    matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(degress));
                }
            }
        }
    }

    @Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("TAIL"))
    private void render_TAIL_SpellEngine(LivingEntity livingEntity, float f, float delta, MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int light, CallbackInfo ci) {
        var client = MinecraftClient.getInstance();
        var isRenderingClientPlayerInFirstPerson = (livingEntity == client.player && !client.gameRenderer.getCamera().isThirdPerson());
        if (!isRenderingClientPlayerInFirstPerson) {
            for (var entry: Synchronized.effectsOf(livingEntity)) {
                var effect = entry.effect();
                var amplifier = entry.amplifier();
                var rendererEntry = CustomModelStatusEffect.entryOf(effect);
                if (rendererEntry != null) {
                    matrixStack.push();
                    if (rendererEntry.args().scaleWithEntity()) {
                        var scale = livingEntity.getScale();
                        matrixStack.scale(scale, scale, scale);
                    }
                    rendererEntry.renderer().renderEffect(amplifier, livingEntity, delta, matrixStack, vertexConsumerProvider, light);
                    matrixStack.pop();
                }
            }
        }
    }

    // Color tinting based on status effect

//    @WrapOperation(
//            method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
//            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/model/EntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V")
//    )
//    private void modelRender_WrapColor(
//            // Mixin parameters
//            EntityModel instance, MatrixStack matrices, VertexConsumer vertices, int light, int overlay, int color, Operation<Void> original,
//            // Context parameters
//            LivingEntity entity, float f, float g, MatrixStack contextMatrixStack, VertexConsumerProvider contextVertexConsumerProvider, int contextLight
//    ) {
//        if (entity.hasStatusEffect(SkillEffects.PHASE_SHIFT.entry)) {
//            var newColor = 0x80ffccff; // (int)Color.ARCANE.alpha(0.4F).toARGB();
//            original.call(instance, matrices, vertices, light, overlay, newColor);
//        } else {
//            original.call(instance, matrices, vertices, light, overlay, color);
//        }
//    }
}
