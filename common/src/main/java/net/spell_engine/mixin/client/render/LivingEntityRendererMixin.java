package net.spell_engine.mixin.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.RotationAxis;
import net.spell_engine.api.effect.CustomModelStatusEffect;
import net.spell_engine.api.effect.EntityTints;
import net.spell_engine.api.effect.Synchronized;
import net.spell_engine.internals.casting.SpellCaster;
import net.spell_engine.client.render.extension.EntityRenderStateExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    private static final String RENDER = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V";

    @Inject(method = RENDER, at = @At("HEAD"))
    private void render_HEAD_SpellEngine(LivingEntityRenderState state, MatrixStack matrixStack, OrderedRenderCommandQueue queue, CameraRenderState cameraState, CallbackInfo ci) {
        if (!(((EntityRenderStateExtension) state).spellEngine_getEntity() instanceof LivingEntity livingEntity)) {
            return;
        }
        var delta = ((EntityRenderStateExtension) state).spellEngine_getTickDelta();
        EntityTints.Current.set(EntityTints.currentTint(livingEntity));
        if (livingEntity instanceof SpellCaster.Player caster) {
            var process = caster.getSpellCastProcess();
            if (process != null) {
                var spell = process.spell().value();
                if (spell.active != null && spell.active.cast != null && spell.active.cast.animation_spin != 0) {
                    var ticks = process.spellCastTicksSoFar(livingEntity.getEntityWorld().getTime());
                    var spin = spell.active.cast.animation_spin;
                    var turn = spin / (process.channelInterval(livingEntity) / 20F);
                    var degress = turn * ticks + delta * turn;
                    matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(degress));
                }
            }
        }
    }

    @Inject(method = RENDER, at = @At("TAIL"))
    private void render_TAIL_SpellEngine(LivingEntityRenderState state, MatrixStack matrixStack, OrderedRenderCommandQueue queue, CameraRenderState cameraState, CallbackInfo ci) {
        if (!(((EntityRenderStateExtension) state).spellEngine_getEntity() instanceof LivingEntity livingEntity)) {
            return;
        }
        var delta = ((EntityRenderStateExtension) state).spellEngine_getTickDelta();
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
                    rendererEntry.renderer().renderEffect(entry.appliedAtWorldTime(), amplifier, livingEntity, delta, matrixStack, queue, state.light);
                    matrixStack.pop();
                }
            }
        }
        EntityTints.Current.clear();
    }

    // MARK: Entity tint (see EntityTints)

    /// When the active tint carries transparency, the body needs a blending-capable layer —
    /// same one vanilla uses for spectators/invisible-to-teammates rendering.
    @Inject(method = "getRenderLayer(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;ZZZ)Lnet/minecraft/client/render/RenderLayer;", at = @At("RETURN"), cancellable = true)
    private void getRenderLayer_RETURN_SpellEngine_Tint(LivingEntityRenderState state, boolean showBody, boolean translucent, boolean showOutline, CallbackInfoReturnable<RenderLayer> cir) {
        if (EntityTints.Current.isTranslucent() && showBody && !translucent && cir.getReturnValue() != null) {
            var texture = ((LivingEntityRenderer) (Object) this).getTexture(state);
            cir.setReturnValue(RenderLayers.itemEntityTranslucentCull(texture));
        }
    }
}
