package net.spell_engine.mixin.client.render.tint;

import net.minecraft.client.render.command.BatchingRenderCommandQueue;
import net.spell_engine.api.effect.EntityTints;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/// Entity tint (see [EntityTints]) on the 1.21.9+ command queue. Rendering is split into submission
/// (`LivingEntityRenderer.render`, where the tint is active) and drawing (`ModelCommandRenderer`, later,
/// when it is not), so the draw-time `ModelPart.renderCuboids` hook of 1.21.1 can no longer see the tint.
/// Every model submission carries a `tintedColor` instead — multiplying it here, at submission, tints the
/// body, vanilla and modded armor and any other model pass issued while the entity renders, exactly like
/// the old per-part multiply did.
@Mixin(BatchingRenderCommandQueue.class)
public class BatchingRenderCommandQueueTintMixin {
    // submitModel(model, state, matrices, renderLayer, int light, int overlay, int tintedColor, sprite, int outlineColor, crumbling)
    @ModifyVariable(method = "submitModel", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private int spellEngine_tintSubmittedModel(int tintedColor) {
        return EntityTints.Current.apply(tintedColor);
    }

    // submitModelPart(part, matrices, renderLayer, int light, int overlay, sprite, sheeted, hasGlint, int tintedColor, crumbling, int)
    @ModifyVariable(method = "submitModelPart", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private int spellEngine_tintSubmittedModelPart(int tintedColor) {
        return EntityTints.Current.apply(tintedColor);
    }
}
