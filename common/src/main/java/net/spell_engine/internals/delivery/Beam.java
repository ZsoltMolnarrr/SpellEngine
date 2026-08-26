package net.spell_engine.internals.delivery;

import net.minecraft.world.phys.Vec3;
import net.spell_engine.api.spell.Spell;

public class Beam {
    public record Position(Vec3 origin, Vec3 end, float length, boolean hitBlock) {  }
    public record Rendered(Position position, Spell.Target.Beam appearance) {  }
}
