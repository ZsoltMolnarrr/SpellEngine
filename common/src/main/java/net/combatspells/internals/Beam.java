package net.combatspells.internals;

import net.combatspells.api.spell.Spell;
import net.minecraft.util.math.Vec3d;

public class Beam {
    public record Position(Vec3d origin, Vec3d end, float length, boolean hitBlock) {  }
    public record Rendered(Position position, Spell.Release.Target.Beam appearance) {  }
}
