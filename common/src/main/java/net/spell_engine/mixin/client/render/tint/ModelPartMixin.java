package net.spell_engine.mixin.client.render.tint;

import net.minecraft.client.model.ModelPart;
import net.spell_engine.api.effect.EntityTints;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ModelPart.class)
public class ModelPartMixin {
    /// Targets `renderCuboids` (the per-part leaf call) instead of `render`, because `render`
    /// recurses into children passing the color along — multiplying there would compound the tint
    /// once per hierarchy depth. `renderCuboids` runs exactly once per part, and Sodium's cuboid
    /// hook consumes the color argument flowing through here, so this works with Sodium as well.
    @ModifyVariable(method = "renderCuboids", at = @At("HEAD"), index = 5, argsOnly = true)
    private int spellEngine_applyEntityTint(int color) {
        return EntityTints.Current.apply(color);
    }
}
