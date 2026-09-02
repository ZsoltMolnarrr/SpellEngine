package net.spell_engine.client.render;

import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.util.LightCoordsUtil;
import net.spell_engine.api.render.CustomLayers;
import net.spell_engine.client.compatibility.ShaderCompatibility;
import net.spell_engine.client.util.Color;
import net.spell_engine.client.util.ItemGlowVertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;

/**
 * Item glow on the 1.21.9+ deferred item render path: the glow color is resolved when the item render
 * state is updated for its holder (`ItemModelResolver.updateForTopItem`, see `ItemModelManagerMixin`)
 * and parked on the render state (`ItemRenderStateMixin`); when a layer of the state is submitted, its
 * quads are submitted a second time on the glow layer, right after the item's own `submitItem`
 * (`LayerRenderStateMixin`), so the `EQUAL` depth test of the glow finds the item's depth. Since 26.2 the
 * feature-render phases guarantee that order: a solid item goes to the `solid` phase, and the blending glow
 * layers submitted through `submitCustomGeometry` land in `translucentCustomGeometry`, which every
 * `FeatureRenderDispatcher` executes after `solid` (the 26.1 `ImmediateItemGlowMixin` buffer trick is gone).
 * Translucent item layers (`translucentBlocksAndItems`, executed later) do not get the glow. Under Iris the
 * pipeline is declared as an emissive-entity program instead.
 */
public final class ItemGlowRendering {
    private ItemGlowRendering() { }

    /// Lit up from within, scaled by opacity, so a faint glow warms the item rather than flipping it
    /// to full bright all at once. Sky light is left alone, it is not ours to raise.
    public static int light(Color glow, int light) {
        return LightCoordsUtil.pack(
                Math.max(LightCoordsUtil.block(light), Math.round(15 * glow.alpha())),
                LightCoordsUtil.sky(light));
    }

    /// Submits the glow passes for one item layer. `matrices` must already carry the layer's display
    /// transform (it does at the `submitItem` call site of `LayerRenderState.submit`).
    public static void submitGlow(Color glow, List<BakedQuad> quads, PoseStack matrices, SubmitNodeCollector queue, int light, int overlay) {
        if (quads.isEmpty()) {
            return;
        }
        // `putBakedQuad` reads color/light/overlay from a QuadInstance (26.1: `putBulkData` is gone). Color is
        // white here, the glow layers ignore or override it; the glow-raised light is what the item was lit with.
        var instance = new QuadInstance();
        instance.setColor(-1);
        instance.setLightCoords(light);
        instance.setOverlayCoords(overlay);

        // The luminance pass: glint program, color x gain through the color modulator, UVs scrolled by the shader.
        // Its vertex format is POSITION_TEXTURE, so the color/overlay/light/normal of the item quads are dropped.
        var uvScale = CustomLayers.itemGlowUvScale(quads);
        queue.submitCustomGeometry(matrices, CustomLayers.itemGlow(glow), (entry, vertexConsumer) -> {
            VertexConsumer glint = new ItemGlowVertexConsumer(vertexConsumer, Color.WHITE, uvScale, false);
            for (var quad : quads) {
                glint.putBakedQuad(entry, quad, instance);
            }
        });

        // Bloom is a shader pack's doing, and it only blooms what it reads as emissive. Without a pack this
        // pass would only wash the item out with a flat coat and buy nothing.
        if (ShaderCompatibility.isShaderPackInUse()) {
            // Opacity folded into the color, as for the shimmer: the additive blend ignores alpha.
            var tint = new Color(glow.red() * glow.alpha(), glow.green() * glow.alpha(), glow.blue() * glow.alpha(), 1F);
            queue.submitCustomGeometry(matrices, CustomLayers.itemGlowEmissive(), (entry, vertexConsumer) -> {
                VertexConsumer emissive = new ItemGlowVertexConsumer(vertexConsumer, tint, uvScale, true);
                for (var quad : quads) {
                    emissive.putBakedQuad(entry, quad, instance);
                }
            });
        }
    }
}
