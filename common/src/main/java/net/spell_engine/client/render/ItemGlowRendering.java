package net.spell_engine.client.render;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.spell_engine.api.render.CustomLayers;
import net.spell_engine.client.compatibility.ShaderCompatibility;
import net.spell_engine.client.util.Color;
import net.spell_engine.client.util.ItemGlowVertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;

/**
 * Item glow on the 1.21.9+ deferred item render path: the glow color is resolved when the item render
 * state is updated for its holder (`ItemModelManager.updateForLivingEntity`, see `ItemModelManagerMixin`)
 * and parked on the render state (`ItemRenderStateMixin`); when the state is rendered, its quads are
 * submitted a second time on the glow layer, after the item's own submission, so the `EQUAL` depth test
 * of the glow finds the item's depth. Submission order alone is not enough: the `Immediate` flushes its
 * fallback buffer before the fixed layer buffers, so the glow layer still needs its own buffer
 * (`ImmediateItemGlowMixin`); under Iris the pipeline is declared as an emissive-entity program instead.
 */
public final class ItemGlowRendering {
    private ItemGlowRendering() { }

    /// Lit up from within, scaled by opacity, so a faint glow warms the item rather than flipping it
    /// to full bright all at once. Sky light is left alone, it is not ours to raise.
    public static int light(Color glow, int light) {
        return LightTexture.pack(
                Math.max(LightTexture.block(light), Math.round(15 * glow.alpha())),
                LightTexture.sky(light));
    }

    /// Submits the glow passes for one item layer (already positioned by the layer's display transform).
    public static void submitGlow(Color glow, List<BakedQuad> quads, PoseStack matrices, SubmitNodeCollector queue, int light, int overlay) {
        if (quads.isEmpty()) {
            return;
        }
        // The luminance pass: glint program, color x gain through the color modulator, UVs scrolled by the shader.
        // Its vertex format is POSITION_TEXTURE, so the color/overlay/light/normal of the item quads are dropped.
        var uvScale = CustomLayers.itemGlowUvScale(quads);
        queue.submitCustomGeometry(matrices, CustomLayers.itemGlow(glow), (entry, vertexConsumer) -> {
            VertexConsumer glint = new ItemGlowVertexConsumer(vertexConsumer, Color.WHITE, uvScale, false);
            for (var quad : quads) {
                glint.putBulkData(entry, quad, 1F, 1F, 1F, 1F, light, overlay);
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
                    emissive.putBulkData(entry, quad, 1F, 1F, 1F, 1F, light, overlay);
                }
            });
        }
    }
}
