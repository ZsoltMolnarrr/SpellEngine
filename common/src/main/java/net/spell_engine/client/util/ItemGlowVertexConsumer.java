package net.spell_engine.client.util;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.spell_engine.api.render.CustomLayers;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Feeds item quads to the emissive glow layer as the glint would have seen them: streak UVs instead of
 * atlas UVs, glow color instead of the item's own tint.
 * <p>
 * The glint shader scrolls its texture itself, through its `TextureMat` uniform. The emissive shader
 * declares no such uniform and samples raw `UV0`, which for an item is its slice of the block atlas -
 * a still sliver of the streaks. The scroll is affine though, and interpolating transformed UVs is the
 * same as transforming interpolated ones, so applying it per vertex here lands the identical shimmer.
 * <p>
 * Deliberately does not override the packed `vertex` overload: its default implementation fans out into
 * the calls below, which is what gives the overrides a chance to run at all.
 */
public class ItemGlowVertexConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private final Matrix4f textureMatrix;
    private final Vector3f scratch = new Vector3f();
    private final int red, green, blue, alpha;

    @Override
    public VertexConsumer setColor(int argb) {
        return setColor(net.minecraft.util.ARGB.red(argb), net.minecraft.util.ARGB.green(argb), net.minecraft.util.ARGB.blue(argb), net.minecraft.util.ARGB.alpha(argb));
    }

    @Override
    public VertexConsumer setLineWidth(float width) {
        delegate.setLineWidth(width);
        return this;
    }

    private final float uvScaleU, uvScaleV;

    public ItemGlowVertexConsumer(VertexConsumer delegate, Color color) {
        this(delegate, color, new org.joml.Vector2f(1F, 1F), true);
    }

    /// @param uvScale multiplies the atlas UVs (per axis) before the scroll (atlas-size compensation, see
    ///                `CustomLayers.itemGlowUvScale`)
    /// @param scroll  apply the scroll matrix here (emissive pass); false when the shader applies it (glint pass)
    public ItemGlowVertexConsumer(VertexConsumer delegate, Color color, org.joml.Vector2f uvScale, boolean scroll) {
        this.delegate = delegate;
        this.uvScaleU = uvScale.x;
        this.uvScaleV = uvScale.y;
        this.textureMatrix = scroll ? CustomLayers.itemGlowTextureMatrix() : new Matrix4f();
        var tint = color.toIntFormat();
        this.red = tint.red();
        this.green = tint.green();
        this.blue = tint.blue();
        this.alpha = tint.alpha();
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        var scrolled = textureMatrix.transformPosition(scratch.set(u * uvScaleU, v * uvScaleV, 0F));
        delegate.setUv(scrolled.x(), scrolled.y());
        return this;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        delegate.setColor(this.red, this.green, this.blue, this.alpha);
        return this;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        delegate.addVertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        delegate.setUv1(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        delegate.setUv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        delegate.setNormal(x, y, z);
        return this;
    }
}
