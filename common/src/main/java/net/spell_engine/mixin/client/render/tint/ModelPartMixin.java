package net.spell_engine.mixin.client.render.tint;

import net.minecraft.client.model.ModelPart;
import net.spell_engine.api.effect.EntityTints;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/// Entity tint at the model-part level (see [EntityTints.Current]).
///
/// Targets `renderCuboids` (the per-part leaf call) instead of `render`, because `render`
/// recurses into children passing the color along — multiplying there would compound the tint
/// once per hierarchy depth. `renderCuboids` runs exactly once per part, and Sodium's cuboid
/// hook consumes the color arguments flowing through here, so this works with Sodium as well.
///
/// 1.20.1 carries the color as four floats — `renderCuboids(MatrixStack.Entry, VertexConsumer,
/// int light, int overlay, float red, float green, float blue, float alpha)` — rather than the
/// packed ARGB int of 1.21, so the multiply is applied per channel, one `@ModifyVariable` each
/// (`ordinal` = position among the float arguments: 0 red, 1 green, 2 blue, 3 alpha). The tint's
/// channels come from the same ARGB [EntityTints.Current] holds; multiplying a channel by
/// `tint / 255` is exactly what the packed-int `EntityTints.multiply` did on 1.21.
@Mixin(ModelPart.class)
public class ModelPartMixin {
    private static float spellEngine_tintChannel(int shift) {
        // `apply(NEUTRAL)` yields the current tint itself (NEUTRAL is the multiplicative identity).
        int argb = EntityTints.Current.apply(EntityTints.NEUTRAL);
        return ((argb >>> shift) & 0xFF) / 255F;
    }

    @ModifyVariable(method = "renderCuboids", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private float spellEngine_applyEntityTintRed(float red) {
        return EntityTints.Current.isActive() ? red * spellEngine_tintChannel(16) : red;
    }

    @ModifyVariable(method = "renderCuboids", at = @At("HEAD"), ordinal = 1, argsOnly = true)
    private float spellEngine_applyEntityTintGreen(float green) {
        return EntityTints.Current.isActive() ? green * spellEngine_tintChannel(8) : green;
    }

    @ModifyVariable(method = "renderCuboids", at = @At("HEAD"), ordinal = 2, argsOnly = true)
    private float spellEngine_applyEntityTintBlue(float blue) {
        return EntityTints.Current.isActive() ? blue * spellEngine_tintChannel(0) : blue;
    }

    @ModifyVariable(method = "renderCuboids", at = @At("HEAD"), ordinal = 3, argsOnly = true)
    private float spellEngine_applyEntityTintAlpha(float alpha) {
        return EntityTints.Current.isActive() ? alpha * spellEngine_tintChannel(24) : alpha;
    }
}
