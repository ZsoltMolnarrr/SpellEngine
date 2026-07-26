package net.spell_engine.client.render;

import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexConsumers;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.spell_engine.api.effect.GlowingItemStatusEffect;
import net.spell_engine.api.render.CustomLayers;
import net.spell_engine.client.compatibility.ShaderCompatibility;
import net.spell_engine.client.util.Color;
import net.spell_engine.client.util.ItemGlowVertexConsumer;
import org.jetbrains.annotations.Nullable;

/**
 * The glow of the item currently being drawn, resolved once at the top of `ItemRenderer.renderItem`
 * and held for the length of that call.
 * <p>
 * The pieces that need it are scattered: the light the item is drawn at is a local of one overload,
 * and the vertex consumer is built by static factories that receive neither the item nor its holder.
 * Held here, all of them can reach it.
 * <p>
 * Rendering happens on a single thread and the calls are strictly nested within one `renderItem`,
 * so a single slot suffices - the same reasoning the holder field used before it moved here.
 */
public final class ItemGlowRendering {
    private ItemGlowRendering() { }

    @Nullable
    private static Color current;

    /// Resolves the glow the holder casts onto the item about to be drawn. A `null` holder is an item
    /// with nobody behind it - one in the GUI, dropped on the ground, or hung in an item frame - and
    /// nothing glows those.
    public static void begin(@Nullable LivingEntity holder, ItemStack stack) {
        current = holder != null ? GlowingItemStatusEffect.resolve(holder, stack) : null;
    }

    /// Cleared so an item never leaks its glow onto the next one drawn.
    public static void end() {
        current = null;
    }

    /// Lit up from within, scaled by opacity, so a faint glow warms the item rather than flipping it
    /// to full bright all at once. Sky light is left alone, it is not ours to raise.
    public static int light(int light) {
        var glow = current;
        if (glow == null) {
            return light;
        }
        return LightmapTextureManager.pack(
                Math.max(LightmapTextureManager.getBlockLightCoordinates(light), Math.round(15 * glow.alpha())),
                LightmapTextureManager.getSkyLightCoordinates(light));
    }

    /// The item's own consumer with the glow layers alongside it, the way the vanilla glint rides along.
    /// Returned unchanged when nothing glows.
    public static VertexConsumer glowing(VertexConsumerProvider vertexConsumers, VertexConsumer vertices) {
        var glow = current;
        if (glow == null) {
            return vertices;
        }

        // This layer carries the luminance: its gain drives the streaks up into the clamp, which the
        // emissive pass cannot do, since a vertex color has nowhere above 1 to go.
        var glowing = VertexConsumers.union(vertexConsumers.getBuffer(CustomLayers.itemGlow(glow)), vertices);

        // Bloom is a shader pack's doing, not the game's, and it only blooms what it reads as emissive.
        // Without a pack, this pass would only wash the item out with a flat coat and buy nothing.
        if (ShaderCompatibility.isShaderPackInUse()) {
            // Opacity is folded into the color, as it is for the shimmer: the blend adds the source
            // outright, so alpha is not a factor in it, and stacks would otherwise not dim the coat at all.
            var tint = new Color(
                    glow.red() * glow.alpha(),
                    glow.green() * glow.alpha(),
                    glow.blue() * glow.alpha(),
                    1F);
            var emissive = new ItemGlowVertexConsumer(vertexConsumers.getBuffer(CustomLayers.itemGlowEmissive()), tint);
            glowing = VertexConsumers.union(glowing, emissive);
        }

        return glowing;
    }
}
