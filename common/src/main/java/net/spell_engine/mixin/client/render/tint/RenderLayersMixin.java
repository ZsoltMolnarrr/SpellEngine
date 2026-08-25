package net.spell_engine.mixin.client.render.tint;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.util.Identifier;
import net.spell_engine.api.effect.EntityTints;
import net.spell_engine.api.render.CustomLayers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/// Armor cutout layers can't alpha-blend, so a translucently tinted entity (see [EntityTints]) would render
/// fully opaque armor. While such a tint is active for the entity being submitted, the vanilla armor layer
/// is swapped for a translucent-blend clone (`CustomLayers.armorCutoutNoCullTranslucent`, declared to Iris as
/// an entity-translucent program). Every other armor draw stays on vanilla's own pipeline — so it renders
/// exactly like vanilla, including under shader packs.
@Mixin(RenderLayers.class)
public abstract class RenderLayersMixin {
    @Inject(method = "armorCutoutNoCull", at = @At("HEAD"), cancellable = true)
    private static void armorCutoutNoCull_HEAD_SpellEngine_Tint(Identifier texture, CallbackInfoReturnable<RenderLayer> cir) {
        if (EntityTints.Current.isTranslucent()) {
            cir.setReturnValue(CustomLayers.armorCutoutNoCullTranslucent(texture));
        }
    }
}
