package net.spell_engine.internals.delivery;

import net.minecraft.util.math.Vec3d;
import net.spell_engine.api.spell.Spell;

public class Beam {
    public record Position(Vec3d origin, Vec3d end, float length, boolean hitBlock) {  }
    public record Rendered(Position position, Spell.Target.Beam appearance) {  }
}
