package net.spell_engine.mixin.client.render.tint;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.LayeringTransform;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

/// Armor cutout layers can't alpha-blend, so tinted entities would render fully opaque armor.
/// Blending lives in the pipeline since 1.21.11, so the vanilla armor layer is swapped for one over a
/// translucent-blend clone of `ARMOR_CUTOUT_NO_CULL` (same setup otherwise). Blending with alpha 1 is a
/// passthrough, so untinted armor is unaffected. Idempotent alongside other mods doing the same.
@Mixin(RenderLayers.class)
public abstract class RenderLayersMixin {
    @Unique
    private static final RenderPipeline SPELL_ENGINE_ARMOR_TRANSLUCENT = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation(Identifier.of("spell_engine", "pipeline/armor_cutout_no_cull_translucent"))
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withShaderDefine("NO_OVERLAY")
            .withShaderDefine("PER_FACE_LIGHTING")
            .withCull(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .build();

    @Unique
    private static final Function<Identifier, RenderLayer> SPELL_ENGINE_ARMOR_LAYER = Util.memoize(texture ->
            RenderLayer.of("armor_cutout_no_cull", RenderSetup.builder(SPELL_ENGINE_ARMOR_TRANSLUCENT)
                    .texture("Sampler0", texture)
                    .useLightmap()
                    .useOverlay()
                    .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .crumbling()
                    .outlineMode(RenderSetup.OutlineMode.AFFECTS_OUTLINE)
                    .build()));

    @Inject(method = "armorCutoutNoCull", at = @At("HEAD"), cancellable = true)
    private static void armorCutoutNoCull_HEAD_SpellEngine_Tint(Identifier texture, CallbackInfoReturnable<RenderLayer> cir) {
        cir.setReturnValue(SPELL_ENGINE_ARMOR_LAYER.apply(texture));
    }
}
