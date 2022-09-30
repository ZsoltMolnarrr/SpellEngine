package net.combatspells.utils;

import net.combatspells.api.spell.ParticleBatch;
import net.combatspells.network.Packets;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;

import java.util.Random;

public class ParticleHelper {
    private static Random rng = new Random();

    public static void sendBatches(Entity trackedEntity, Vec3d position, ParticleBatch[] batches) {
        if (batches == null || batches.length == 0) {
            return;
        }
        var packet = new Packets.ParticleBatches(position, batches).write();
        PlayerLookup.tracking(trackedEntity).forEach(serverPlayer -> {
            try {
                if (ServerPlayNetworking.canSend(serverPlayer, Packets.ParticleBatches.ID)) {
                    ServerPlayNetworking.send(serverPlayer, Packets.ParticleBatches.ID, packet);
                }
            } catch (Exception e){
                e.printStackTrace();
            }
        });
    }

    public static void play(World world, Vec3d origin, ParticleBatch effect) {
        play(world, origin, 0, 0, effect);
    }

    public static void play(World world, Vec3d origin, float yaw, float pitch, ParticleBatch batch) {
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

    private static Vec3d direction(ParticleBatch batch, float yaw, float pitch) {
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
