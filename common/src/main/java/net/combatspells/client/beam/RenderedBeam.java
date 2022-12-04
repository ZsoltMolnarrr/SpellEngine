package net.combatspells.client.beam;

import net.minecraft.util.math.Vec3d;

public record RenderedBeam(Vec3d origin, Vec3d end, float length, boolean hitBlock) {
}
