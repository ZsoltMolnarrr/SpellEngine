package net.spell_engine.client.util;

import net.minecraft.util.math.Vec2f;

public record Rect(Vec2f topLeft, Vec2f bottomRight) {
    public boolean contains(double x, double y) {
        return (x >= topLeft.x && x <= bottomRight.x) && (y >= topLeft.y && y <= bottomRight.y);
    }
}
