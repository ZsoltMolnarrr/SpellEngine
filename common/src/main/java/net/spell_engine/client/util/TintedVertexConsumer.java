package net.spell_engine.client.util;

import net.minecraft.client.render.VertexConsumer;

/**
 * Replaces the color of everything drawn through it with a fixed one.
 * <p>
 * Item quads carry their own tint, which is white for nearly every item. Handed to an emissive layer
 * as is, they would coat the item in white and bloom white. Forcing the color here is what lets a
 * single emissive layer serve every glow color, instead of one baked layer per color.
 * <p>
 * Deliberately does not override the packed `vertex` overload: its default implementation fans out
 * into the calls below, which is exactly how the color override gets a chance to run.
 */
public class TintedVertexConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private final int red, green, blue, alpha;

    public TintedVertexConsumer(VertexConsumer delegate, Color color) {
        this.delegate = delegate;
        var tint = color.toIntFormat();
        this.red = tint.red();
        this.green = tint.green();
        this.blue = tint.blue();
        this.alpha = tint.alpha();
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
    public VertexConsumer texture(float u, float v) {
        delegate.texture(u, v);
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
