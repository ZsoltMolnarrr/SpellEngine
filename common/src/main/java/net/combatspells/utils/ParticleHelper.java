package net.combatspells.utils;

import net.combatspells.api.spell.ParticleBatch;
import net.combatspells.network.Packets;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Matrix3f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;

import java.util.Random;

public class ParticleHelper {
    private static Random rng = new Random();

    public static void sendBatches(Entity trackedEntity, ParticleBatch[] batches) {
        if (batches == null || batches.length == 0) {
            return;
        }
        var packet = new Packets.ParticleBatches(trackedEntity.getId(), batches).write();
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

    public static void play(World world, Entity source, ParticleBatch effect) {
        play(world, source, 0, 0, effect);
    }

    public static void play(World world, Entity source, float yaw, float pitch, ParticleBatch batch) {
        try {

            var origin = origin(source, batch.origin);

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

    private static Vec3d origin(Entity entity, ParticleBatch.Origin origin) {
        switch (origin) {
            case FEET -> {
                return entity.getPos();
            }
            case CENTER -> {
                return entity.getPos().add(0, entity.getHeight() / 2F, 0);
            }
            case HANDS -> {
                return entity.getPos();
            }
        }
        assert true;
        return entity.getPos();
    }

    private static Vec3d direction(ParticleBatch batch, float yaw, float pitch) {
        switch (batch.shape) {
            case CIRCLE -> {
                var speedRange = batch.max_speed - batch.min_speed;
                var randZ = batch.min_speed + ((rng.nextFloat() - 0.5F) * 2F) * speedRange;
                var randX = batch.min_speed + ((rng.nextFloat() - 0.5F) * 2F) * speedRange;
                var direction = new Vec3d(randX, 0, randZ);
                if (yaw != 0) {
                    direction = direction.rotateY((float) Math.toRadians(yaw));
                }
                if (pitch != 0) {
                    var pitchRad = Math.toRadians(pitch);
                    var yawRad = Math.toRadians(yaw);
                    direction = direction.rotateZ((float) (Math.sin(yawRad) * pitchRad));
                    direction = direction.rotateX((float) (Math.cos(yawRad) * pitchRad));
                }
                return direction;
            }
        }
        assert true;
        return Vec3d.ZERO;
    }
}
