package net.spell_engine.client.render;

import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.math.MatrixStack;
import net.spell_engine.api.render.CustomLayers;
import net.spell_engine.client.util.Color;
import net.spell_engine.client.util.ItemGlowVertexConsumer;

import java.util.List;

/**
 * Item glow on the 1.21.9+ deferred item render path: the glow color is resolved when the item render
 * state is updated for its holder (`ItemModelManager.updateForLivingEntity`, see `ItemModelManagerMixin`)
 * and parked on the render state (`ItemRenderStateMixin`); when the state is rendered, its quads are
 * submitted a second time on the glow layer, after the item's own submission, so the `EQUAL` depth test
 * of the glow finds the item's depth. Ordering is explicit now, so neither the `Immediate` buffer hack
 * nor the Iris decal marking of 1.21.1 are needed.
 */
public final class ItemGlowRendering {
    private ItemGlowRendering() { }

    /// Lit up from within, scaled by opacity, so a faint glow warms the item rather than flipping it
    /// to full bright all at once. Sky light is left alone, it is not ours to raise.
    public static int light(Color glow, int light) {
        return LightmapTextureManager.pack(
                Math.max(LightmapTextureManager.getBlockLightCoordinates(light), Math.round(15 * glow.alpha())),
                LightmapTextureManager.getSkyLightCoordinates(light));
    }

    /// Submits the glow pass for one item layer (already positioned by the layer's display transform).
    public static void submitGlow(Color glow, List<BakedQuad> quads, MatrixStack matrices, OrderedRenderCommandQueue queue, int light, int overlay) {
        if (quads.isEmpty()) {
            return;
        }
        // Opacity is folded into the color: the blend adds the source outright, so alpha is not a factor
        // in it. The gain drives the mid tones of the streaks up into the clamp (coverage, not peak).
        var intensity = glow.alpha() * CustomLayers.itemGlowGain;
        var tint = new Color(
                Math.min(1F, glow.red() * intensity),
                Math.min(1F, glow.green() * intensity),
                Math.min(1F, glow.blue() * intensity),
                1F);
        queue.submitCustom(matrices, CustomLayers.itemGlow(glow), (entry, vertexConsumer) -> {
            VertexConsumer glowing = new ItemGlowVertexConsumer(vertexConsumer, tint);
            for (var quad : quads) {
                glowing.quad(entry, quad, 1F, 1F, 1F, 1F, light, overlay);
            }
        });
    }
}
