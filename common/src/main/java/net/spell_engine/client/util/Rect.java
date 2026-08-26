package net.spell_engine.client.util;

import net.minecraft.world.phys.Vec2;

public record Rect(Vec2 topLeft, Vec2 bottomRight) {
    public boolean contains(double x, double y) {
        return (x >= topLeft.x && x <= bottomRight.x) && (y >= topLeft.y && y <= bottomRight.y);
    }
}
