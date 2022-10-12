package net.combatspells.utils;

import net.combatspells.api.spell.ParticleBatch;
import net.combatspells.network.Packets;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ParticleHelper {
    private static Random rng = new Random();

    public static void sendBatches(Entity trackedEntity, ParticleBatch[] batches) {
        sendBatches(trackedEntity, batches, 1);
    }

    public static void sendBatches(Entity trackedEntity, ParticleBatch[] batches, float countMultiplier) {
        if (batches == null || batches.length == 0) {
            return;
        }
        var packet = new Packets.ParticleBatches(trackedEntity.getId(), batches).write(countMultiplier);
        if (trackedEntity instanceof ServerPlayerEntity serverPlayer) {
            sendWrittenBatchesToPlayer(serverPlayer, packet);
        }
        PlayerLookup.tracking(trackedEntity).forEach(serverPlayer -> {
            sendWrittenBatchesToPlayer(serverPlayer, packet);
        });
    }

    private static void sendWrittenBatchesToPlayer(ServerPlayerEntity serverPlayer, PacketByteBuf packet) {
        try {
            if (ServerPlayNetworking.canSend(serverPlayer, Packets.ParticleBatches.ID)) {
                ServerPlayNetworking.send(serverPlayer, Packets.ParticleBatches.ID, packet);
            }
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public static void play(World world, Entity source, ParticleBatch[] batches) {
        if (batches == null) {
            return;
        }
        for (var batch: batches) {
            play(world, source, 0, 0, batch);
        }
    }

    public static void play(World world, Entity source, ParticleBatch effect) {
        play(world, source, 0, 0, effect);
    }

    public static void play(World world, Entity entity, float yaw, float pitch, ParticleBatch batch) {
        try {
            var id = new Identifier(batch.particle_id);
            var origin = origin(entity, batch.origin).add(offset(entity, batch));;
            var particle = (ParticleEffect) Registry.PARTICLE_TYPE.get(id);
            var count = batch.count;
            if (batch.count < 1) {
                count = rng.nextFloat() < batch.count ? 1 : 0;
            }
            for(int i = 0; i < count; ++i) {
                var direction = direction(batch, yaw, pitch);
                world.addParticle(particle, true,
                        origin.x, origin.y, origin.z,
                        direction.x, direction.y, direction.z);
            }
        } catch (Exception e) {
            System.err.println("Failed to play particle batch");
        }
    }

    public static List<SpawnInstruction> convertToInstructions(Entity entity, float pitch, float yaw, ParticleBatch[] batches) {
        var instructions = new ArrayList<SpawnInstruction>();
        for(var batch: batches) {
            var id = new Identifier(batch.particle_id);
            var origin = origin(entity, batch.origin).add(offset(entity, batch));
            var particle = (ParticleEffect) Registry.PARTICLE_TYPE.get(id);
            var count = batch.count;
            if (batch.count < 1) {
                count = rng.nextFloat() < batch.count ? 1 : 0;
            }
            for(int i = 0; i < count; ++i) {
                var direction = direction(batch, yaw, pitch);
                instructions.add(new SpawnInstruction(particle,
                        origin.x, origin.y, origin.z,
                        direction.x, direction.y, direction.z));
            }
        }
        return instructions;
    }

    public record SpawnInstruction(ParticleEffect particle,
                                   double positionX, double positionY, double positionZ,
                                   double velocityX, double velocityY, double velocityZ) {
        public void perform(World world) {
            try {
                world.addParticle(particle, true,
                        positionX, positionY, positionZ,
                        velocityX, velocityY, velocityZ);
            } catch (Exception e) {
                System.err.println("Failed to perform particle SpawnInstruction");
            }
        }
    }

    private static Vec3d origin(Entity entity, ParticleBatch.Origin origin) {
        switch (origin) {
            case FEET -> {
                return entity.getPos().add(0, entity.getHeight() * 0.1F, 0);
            }
            case CENTER -> {
                return entity.getPos().add(0, entity.getHeight() / 2F, 0);
            }
            case HANDS -> {
                // TODO: Calculate hand positions using animation library
                return entity.getPos().add(0, entity.getHeight(), 0);
            }
        }
        assert true;
        return entity.getPos();
    }

    private static Vec3d offset(Entity entity, ParticleBatch batch) {
        switch (batch.shape) {
            case PIPE -> {
                var x = entity.getWidth();
                var angle = (float) Math.toRadians(rng.nextFloat() * 360F);
                return new Vec3d(x,0,0).rotateY(angle);
            }
            case PILLAR -> {
                var x = randomInRange(0, entity.getWidth());
                var angle = (float) Math.toRadians(rng.nextFloat() * 360F);
                return new Vec3d(x,0,0).rotateY(angle);
            }
        }
        return Vec3d.ZERO;
    }

    private static Vec3d direction(ParticleBatch batch, float yaw, float pitch) {
        switch (batch.shape) {
            case CIRCLE -> {
                var angle = (float) Math.toRadians(rng.nextFloat() * 360F);
//                var randX = randomSignedInRange(batch.min_speed, batch.max_speed);
                var direction = new Vec3d(randomInRange(batch.min_speed, batch.max_speed), 0, 0).rotateY(angle);
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
            case PILLAR, PIPE -> {
                var y = randomInRange(batch.min_speed, batch.max_speed);
                return new Vec3d(0, y, 0);
            }
        }
        assert true;
        return Vec3d.ZERO;
    }

    private static float randomInRange(float min, float max) {
        float range = max - min;
        return min + (range * rng.nextFloat());
    }

    private static float randomSignedInRange(float min, float max) {
        var rand = rng.nextFloat();
        var range = max - min;
        float sign = (rand > 0.5F) ? 1 : (-1);
        var base = sign * min;
        var varied = sign * range * rand;
        return base + varied;
    }
}
