package net.spell_engine.mixin.client.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
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
import net.spell_engine.client.render.tint.EntityTintPass;
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
    private static final String UPDATE_RENDER_STATE = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V";

    @Inject(method = RENDER, at = @At("HEAD"))
    private void render_HEAD_SpellEngine(LivingEntityRenderState state, MatrixStack matrixStack, OrderedRenderCommandQueue queue, CameraRenderState cameraState, CallbackInfo ci) {
        if (!(((EntityRenderStateExtension) state).spellEngine_getEntity() instanceof LivingEntity livingEntity)) {
            return;
        }
        var delta = ((EntityRenderStateExtension) state).spellEngine_getTickDelta();
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
        try {
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
        } finally {
            // Closes the feature pass opened by render_FEATURES_SpellEngine_Tint (the custom effect models above are part of it)
            EntityTintPass.end();
        }
    }

    // MARK: Entity tint (see EntityTints)

    /// The tint is extracted with the rest of the render state, so it travels with the state to the render pass
    @Inject(method = UPDATE_RENDER_STATE, at = @At("TAIL"))
    private void updateRenderState_TAIL_SpellEngine_Tint(LivingEntity entity, LivingEntityRenderState state, float tickDelta, CallbackInfo ci) {
        ((EntityRenderStateExtension) state).spellEngine_setTint(EntityTints.currentTint(entity));
    }

    /// Body: vanilla mixes `getMixColor(state)` (per-renderer color, e.g. wolf/tropical fish) into the body's
    /// `tintedColor` via `ColorHelper.mix` — a channel multiply, so multiplying the tint into that color here
    /// yields exactly `mix(teamTranslucency, mixColor) × tint`. Wrapping the call (not the method) also covers
    /// the renderers that override `getMixColor`.
    @WrapOperation(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;getMixColor(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;)I"))
    private int render_getMixColor_SpellEngine_Tint(LivingEntityRenderer<?, ?, ?> renderer, LivingEntityRenderState state, Operation<Integer> original) {
        return EntityTints.multiply(original.call(renderer, state), ((EntityRenderStateExtension) state).spellEngine_getTint());
    }

    /// When the tint carries transparency, the body needs a blending-capable layer —
    /// same one vanilla uses for spectators/invisible-to-teammates rendering.
    @Inject(method = "getRenderLayer(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;ZZZ)Lnet/minecraft/client/render/RenderLayer;", at = @At("RETURN"), cancellable = true)
    private void getRenderLayer_RETURN_SpellEngine_Tint(LivingEntityRenderState state, boolean showBody, boolean translucent, boolean showOutline, CallbackInfoReturnable<RenderLayer> cir) {
        if (((EntityRenderStateExtension) state).spellEngine_hasTranslucentTint() && showBody && !translucent && cir.getReturnValue() != null) {
            var texture = ((LivingEntityRenderer) (Object) this).getTexture(state);
            cir.setReturnValue(RenderLayers.itemEntityTranslucentCull(texture));
        }
    }

    /// Feature pass: everything submitted after the body (armor, cape, elytra, held items, custom effect models)
    /// carries no tint of its own — open the pass scope so the queue/armor-layer mixins apply this state's tint.
    /// Opened here, after the body submission, so the body (tinted via `getMixColor`) is not tinted twice.
    @Inject(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;shouldRenderFeatures(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;)Z"))
    private void render_FEATURES_SpellEngine_Tint(LivingEntityRenderState state, MatrixStack matrixStack, OrderedRenderCommandQueue queue, CameraRenderState cameraState, CallbackInfo ci) {
        EntityTintPass.begin(state);
    }
}
