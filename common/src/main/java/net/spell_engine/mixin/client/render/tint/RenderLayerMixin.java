package net.spell_engine.mixin.client.render.tint;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(RenderLayer.class)
public abstract class RenderLayerMixin extends RenderPhase {
    // Extends RenderPhase (like the target does) for access to the protected TRANSLUCENT_TRANSPARENCY
    // constant on both platforms, without relying on platform access transformers.
    private RenderLayerMixin(String name, Runnable beginAction, Runnable endAction) {
        super(name, beginAction, endAction);
    }

    /// Armor cutout layers can't alpha-blend, so tinted entities would render fully opaque armor.
    /// Startup-time transparency swap, same as Shoulder Surfing's (idempotent alongside it):
    /// blending with alpha 1 is a passthrough, so untinted armor is unaffected.
    @ModifyArg(
            method = "createArmorCutoutNoCull",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/RenderLayer$MultiPhaseParameters$Builder;transparency(Lnet/minecraft/client/render/RenderPhase$Transparency;)Lnet/minecraft/client/render/RenderLayer$MultiPhaseParameters$Builder;"
            )
    )
    private static RenderPhase.Transparency spellEngine_armorTransparency(RenderPhase.Transparency transparency) {
        return TRANSLUCENT_TRANSPARENCY;
    }
}
