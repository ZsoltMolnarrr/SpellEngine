package net.combatspells.utils;

import net.combatspells.api.Spell;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;

import java.util.Random;

public class ParticleHelper {
    private static Random rng = new Random();

    public static void play(World world, Vec3d origin, Spell.ParticleEffect effect) {
        try {
            var id = new Identifier(effect.id);
            var particle = (ParticleEffect) Registry.PARTICLE_TYPE.get(id);
            for(int i = 0; i < effect.count; ++i) {
                var direction = direction(effect);
                world.addParticle(particle, true,
                        origin.x, origin.y, origin.z,
                        direction.x, direction.y, direction.z);
            }
        } catch (Exception e) {
            System.err.println("Failed to play particle effect");
        }
    }

    private static Vec3d direction(Spell.ParticleEffect effect) {
        switch (effect.shape) {
            case CIRCLE -> {
                var expand = effect.speed;
                var randX = rng.nextFloat() * (expand * 2) - expand;
                var randY = rng.nextFloat() * (expand * 2) - expand;
                return new Vec3d(randX, randY, 0); // TODO rotate
            }
        }
        assert true;
        return Vec3d.ZERO;
    }
}
