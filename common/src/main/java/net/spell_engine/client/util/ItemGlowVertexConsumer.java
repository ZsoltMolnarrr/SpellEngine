package net.spell_engine.client.util;

import net.minecraft.client.render.VertexConsumer;
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

    public ItemGlowVertexConsumer(VertexConsumer delegate, Color color) {
        this.delegate = delegate;
        this.textureMatrix = CustomLayers.itemGlowTextureMatrix();
        var tint = color.toIntFormat();
        this.red = tint.red();
        this.green = tint.green();
        this.blue = tint.blue();
        this.alpha = tint.alpha();
    }

    @Override
    public VertexConsumer texture(float u, float v) {
        var scrolled = textureMatrix.transformPosition(scratch.set(u, v, 0F));
        delegate.texture(scrolled.x(), scrolled.y());
        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        delegate.color(this.red, this.green, this.blue, this.alpha);
        return this;
    }

    @Override
    public VertexConsumer vertex(float x, float y, float z) {
        delegate.vertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer overlay(int u, int v) {
        delegate.overlay(u, v);
        return this;
    }

    @Override
    public VertexConsumer light(int u, int v) {
        delegate.light(u, v);
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        delegate.normal(x, y, z);
        return this;
    }
}
