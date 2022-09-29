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

    public static void play(World world, Vec3d origin, Spell.ParticleBatch effect) {
        play(world, origin, 0, 0, effect);
    }

    public static void play(World world, Vec3d origin, float yaw, float pitch, Spell.ParticleBatch batch) {
        try {
            var id = new Identifier(batch.particle_id);
            var particle = (ParticleEffect) Registry.PARTICLE_TYPE.get(id);
            for(int i = 0; i < batch.count; ++i) {
                var direction = direction(batch, yaw, pitch);
                world.addParticle(particle, true,
                        origin.x, origin.y, origin.z,
                        direction.x, direction.y, direction.z);
            }
        } catch (Exception e) {
            System.err.println("Failed to play particle batch");
        }
    }

    private static Vec3d direction(Spell.ParticleBatch batch, float yaw, float pitch) {
        switch (batch.shape) {
            case CIRCLE -> {
                var speedRange = batch.max_speed - batch.min_speed;
                var randZ = batch.min_speed + ((rng.nextFloat() - 0.5F) * 2F) * speedRange;
                var randX = batch.min_speed + ((rng.nextFloat() - 0.5F) * 2F) * speedRange;
                var direction = new Vec3d(randX, 0, randZ);
                if (yaw != 0) {
                    direction = direction.rotateY(yaw);
                }
                if (pitch != 0) {
                    direction = direction.rotateX(pitch);
                }
                return direction;
            }
        }
        assert true;
        return Vec3d.ZERO;
    }
}
