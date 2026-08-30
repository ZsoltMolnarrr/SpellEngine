package net.spell_engine.mixin.client.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.spell_engine.api.effect.CustomModelStatusEffect;
import net.spell_engine.api.effect.EntityTints;
import net.spell_engine.api.effect.Synchronized;
import net.spell_engine.api.spell.fx.ModelEffectAttachment;
import net.spell_engine.client.render.ModelEffectOperations;
import net.spell_engine.client.render.tint.EntityTintPass;
import net.spell_engine.internals.casting.SpellCaster;
import net.spell_engine.client.render.extension.EntityRenderStateExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    private static final String RENDER = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V";
    private static final String UPDATE_RENDER_STATE = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V";

    /// Cast spin: applied just after the renderer's own `pushPose()` (the single one in `submit`, both in vanilla
    /// and in the NeoForge-patched copy), so the rotation lives inside the pose entry vanilla pops at the end of
    /// `submit`. At HEAD it would land in the *caller's* entry (`EntityRenderDispatcher#submit`) and stay there for
    /// the rest of that entity's frame, spinning the drop shadow and displacing the fire overlay along with the model.
    /// The model transform is unchanged: `pushPose` copies the current entry, so the spin still premultiplies
    /// everything vanilla applies below (bed offset, scale, body rotations).
    @Inject(method = RENDER, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER))
    private void render_CAST_SPIN_SpellEngine(LivingEntityRenderState state, PoseStack matrixStack, SubmitNodeCollector queue, CameraRenderState cameraState, CallbackInfo ci) {
        if (!(((EntityRenderStateExtension) state).spellEngine_getEntity() instanceof LivingEntity livingEntity)) {
            return;
        }
        var delta = ((EntityRenderStateExtension) state).spellEngine_getTickDelta();
        if (livingEntity instanceof SpellCaster.Player caster) {
            var process = caster.getSpellCastProcess();
            if (process != null) {
                var spell = process.spell().value();
                if (spell.active != null && spell.active.cast != null && spell.active.cast.animation_spin != 0) {
                    var ticks = process.spellCastTicksSoFar(livingEntity.level().getGameTime());
                    var spin = spell.active.cast.animation_spin;
                    var turn = spin / (process.channelInterval(livingEntity) / 20F);
                    var degress = turn * ticks + delta * turn;
                    matrixStack.mulPose(Axis.YP.rotationDegrees(degress));
                }
            }
        }
    }

    @Inject(method = RENDER, at = @At("TAIL"))
    private void render_TAIL_SpellEngine(LivingEntityRenderState state, PoseStack matrixStack, SubmitNodeCollector queue, CameraRenderState cameraState, CallbackInfo ci) {
        try {
            if (!(((EntityRenderStateExtension) state).spellEngine_getEntity() instanceof LivingEntity livingEntity)) {
                return;
            }
            var delta = ((EntityRenderStateExtension) state).spellEngine_getTickDelta();
            // Model FX first, then the custom effect models — the submission order the two separate TAIL
            // injectors happened to have before they were merged (see the comment on the method below).
            spellEngine_submitModelFx(state, matrixStack, queue, livingEntity, delta);
            var client = Minecraft.getInstance();
            var isRenderingClientPlayerInFirstPerson = (livingEntity == client.player && !client.gameRenderer.getMainCamera().isDetached());
            if (!isRenderingClientPlayerInFirstPerson) {
                for (var entry: Synchronized.effectsOf(livingEntity)) {
                    var effect = entry.effect();
                    var amplifier = entry.amplifier();
                    var rendererEntry = CustomModelStatusEffect.entryOf(effect);
                    if (rendererEntry != null) {
                        matrixStack.pushPose();
                        if (rendererEntry.args().scaleWithEntity()) {
                            var scale = livingEntity.getScale();
                            matrixStack.scale(scale, scale, scale);
                        }
                        rendererEntry.renderer().renderEffect(entry.appliedAtWorldTime(), amplifier, livingEntity, delta, matrixStack, queue, state.lightCoords);
                        matrixStack.popPose();
                    }
                }
            }
        } finally {
            // Closes the feature pass opened by render_FEATURES_SpellEngine_Tint (the custom effect models above are part of it)
            EntityTintPass.end();
        }
    }

    /// Model FX attached to the entity (`ModelEffectAttachment`). Merged in from the former
    /// `LivingEntityModelFxRendererMixin`, which injected at the very same `submit` TAIL: with two TAIL
    /// injectors the relative order was decided by the order of the two entries in `spell_engine.mixins.json`
    /// (`client.render.LivingEntityModelFxRendererMixin` was listed first, so it ran first — i.e. *before*
    /// `EntityTintPass.end()`, inside the open tint scope). Calling it here, at the top of the `try` block,
    /// reproduces exactly that: same position relative to the effect-model loop, still inside the tint scope.
    @Unique
    private void spellEngine_submitModelFx(LivingEntityRenderState state, PoseStack matrices, SubmitNodeCollector queue, LivingEntity entity, float delta) {
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
    @WrapOperation(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;getModelTint(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)I"))
    private int render_getMixColor_SpellEngine_Tint(LivingEntityRenderer<?, ?, ?> renderer, LivingEntityRenderState state, Operation<Integer> original) {
        return EntityTints.multiply(original.call(renderer, state), ((EntityRenderStateExtension) state).spellEngine_getTint());
    }

    /// When the tint carries transparency, the body needs a blending-capable layer —
    /// same one vanilla uses for spectators/invisible-to-teammates rendering.
    @Inject(method = "getRenderType(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;ZZZ)Lnet/minecraft/client/renderer/rendertype/RenderType;", at = @At("RETURN"), cancellable = true)
    private void getRenderLayer_RETURN_SpellEngine_Tint(LivingEntityRenderState state, boolean showBody, boolean translucent, boolean showOutline, CallbackInfoReturnable<RenderType> cir) {
        if (((EntityRenderStateExtension) state).spellEngine_hasTranslucentTint() && showBody && !translucent && cir.getReturnValue() != null) {
            var texture = ((LivingEntityRenderer) (Object) this).getTextureLocation(state);
            cir.setReturnValue(RenderTypes.entityTranslucentCullItemTarget(texture));
        }
    }

    /// Feature pass: everything submitted after the body (armor, cape, elytra, held items, custom effect models)
    /// carries no tint of its own — open the pass scope so the queue/armor-layer mixins apply this state's tint.
    /// Opened here, after the body submission, so the body (tinted via `getMixColor`) is not tinted twice.
    @Inject(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;shouldRenderLayers(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)Z"))
    private void render_FEATURES_SpellEngine_Tint(LivingEntityRenderState state, PoseStack matrixStack, SubmitNodeCollector queue, CameraRenderState cameraState, CallbackInfo ci) {
        EntityTintPass.begin(state);
    }
}
