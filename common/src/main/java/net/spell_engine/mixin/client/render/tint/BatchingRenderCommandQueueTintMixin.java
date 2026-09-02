package net.spell_engine.mixin.client.render.tint;

import net.minecraft.client.renderer.SubmitNodeCollection;
import net.spell_engine.api.effect.EntityTints;
import net.spell_engine.client.render.tint.EntityTintPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/// Entity tint (see [EntityTints]) for the extra render passes of a living entity on the 1.21.9+ command queue.
/// Rendering is split into submission (`LivingEntityRenderer.render`) and drawing (`ModelCommandRenderer`, later),
/// so the tint has to travel on the submitted command: every model submission carries a `tintedColor`.
/// The body already carries it (mixed in via `getMixColor`, see `LivingEntityRendererMixin`); this multiplies it
/// into everything submitted during the entity's feature pass ([EntityTintPass]): vanilla and modded armor,
/// capes, elytra and other feature models, exactly like the per-part multiply of 1.21.1 did.
@Mixin(SubmitNodeCollection.class)
public class BatchingRenderCommandQueueTintMixin {
    // submitModel(model, state, matrices, renderLayer, int light, int overlay, int tintedColor, sprite, int outlineColor, crumbling)
    @ModifyVariable(method = "submitModel", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private int spellEngine_tintSubmittedModel(int tintedColor) {
        return EntityTintPass.apply(tintedColor);
    }

    // 26.2: `submitModelPart` is a default method of `OrderedSubmitNodeCollector` that wraps the part in a
    // `Model.Simple` and calls `submitModel` above, so model parts are tinted through the same hook.
}
