package net.spell_engine.particle;

import net.minecraft.particle.DefaultParticleType;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.spell_damage.SpellDamageMod;

public class Particles {
    private static class Helper extends DefaultParticleType {
        protected Helper(boolean alwaysShow) {
            super(alwaysShow);
        }
    }
    private static DefaultParticleType createSimple() {
        return new Helper(false);
    }

    public static class Fire {
        public static Identifier ID = new Identifier(SpellDamageMod.ID, "fire");
        public static DefaultParticleType particle = Particles.createSimple();
    }

    public static void register() {
        Registry.register(Registry.PARTICLE_TYPE, Fire.ID, Fire.particle);
    }
}