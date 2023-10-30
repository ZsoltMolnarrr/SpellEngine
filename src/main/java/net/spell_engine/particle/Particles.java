package net.spell_engine.particle;

import net.minecraft.particle.DefaultParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.spell_engine.SpellEngineMod;

import java.util.ArrayList;
import java.util.List;

public class Particles {
    private static class Helper extends DefaultParticleType {
        protected Helper(boolean alwaysShow) {
            super(alwaysShow);
        }
    }
    private static DefaultParticleType createSimple() {
        return new Helper(false);
    }

    public static class ParticleEntry {
        public final Identifier id;
        public final DefaultParticleType particleType = Particles.createSimple();
        public boolean usesCustomTexture = false;
        public ParticleEntry(String name) {
            this.id =  new Identifier(SpellEngineMod.ID, name);
        }
        public ParticleEntry customTexture() {
            this.usesCustomTexture = true;
            return this;
        }
    }

    private static final ArrayList<ParticleEntry> all = new ArrayList<>();
    public static List<ParticleEntry> all() {
        return all;
    }

    private static ParticleEntry particle(String name) {
        var entry = new ParticleEntry(name);
        all.add(entry);
        return entry;
    }


    //    public static final ParticleEntry arcane_spark = new ParticleEntry("arcane_spark");
    public static final ParticleEntry arcane_spell = particle("arcane_spell");
    public static final ParticleEntry arcane_hit = particle("arcane_hit").customTexture();
    public static final ParticleEntry healing_ascend = particle("healing_ascend").customTexture();
    public static final ParticleEntry holy_ascend = particle("holy_ascend").customTexture();
    public static final ParticleEntry holy_hit = particle("holy_hit").customTexture();
    public static final ParticleEntry holy_spark = particle("holy_spark");
    public static final ParticleEntry holy_spark_mini = particle("holy_spark_mini");
    public static final ParticleEntry holy_spell = particle("holy_spell");
    public static final ParticleEntry fire_explosion = particle("fire_explosion").customTexture();
    public static final ParticleEntry flame = particle("flame");
    public static final ParticleEntry frost_hit = particle("frost_hit").customTexture();
    public static final ParticleEntry frost_shard = particle("frost_shard").customTexture();
    public static final ParticleEntry snowflake = particle("snowflake");
    public static final ParticleEntry dripping_blood = particle("dripping_blood");
    public static final ParticleEntry roots = particle("roots").customTexture();


    public static void register() {
        for(var entry: all) {
            Registry.register(Registries.PARTICLE_TYPE, entry.id, entry.particleType);
        }
    }
}