package net.spell_engine.particle;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.util.math.Quaternion;
import net.spell_engine.api.spell.ParticleBatch;
import net.spell_engine.internals.SpellHelper;
import net.spell_engine.network.Packets;
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
import java.util.Collection;
import java.util.List;
import java.util.Random;

public class ParticleHelper {
    private static Random rng = new Random();

    public static void sendBatches(Entity trackedEntity, ParticleBatch[] batches) {
        sendBatches(trackedEntity, batches, 1, PlayerLookup.tracking(trackedEntity));
    }

    public static void sendBatches(Entity trackedEntity, ParticleBatch[] batches, float countMultiplier, Collection<ServerPlayerEntity> trackers) {
        if (batches == null || batches.length == 0) {
            return;
        }
        int sourceEntityId = trackedEntity.getId();
//        Packets.ParticleBatches.SourceType sourceType = trackedEntity.isAlive() ?
//                Packets.ParticleBatches.SourceType.ENTITY : Packets.ParticleBatches.SourceType.COORDINATE;
        var sourceType = Packets.ParticleBatches.SourceType.COORDINATE;
        ArrayList<Packets.ParticleBatches.Spawn> spawns = new ArrayList<>();
        for(var batch : batches) {
            Vec3d sourceLocation = Vec3d.ZERO;
            switch (sourceType) {
                case ENTITY -> {
                }
                case COORDINATE -> {
                    sourceLocation = origin(trackedEntity, batch.origin);
                }
            }
            spawns.add(new Packets.ParticleBatches.Spawn(sourceEntityId, sourceLocation, batch));
        }
        var packet = new Packets.ParticleBatches(sourceType, spawns).write(countMultiplier);
        if (trackedEntity instanceof ServerPlayerEntity serverPlayer) {
            sendWrittenBatchesToPlayer(serverPlayer, packet);
        }
        trackers.forEach(serverPlayer -> {
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

    public static void play(World world, Entity source, ParticleBatch batch) {
        play(world, source, 0, 0, batch);
    }

    public static void play(World world, Entity entity, float yaw, float pitch, ParticleBatch batch) {
        play(world, origin(entity, batch.origin), entity.getWidth(), yaw, pitch, batch);
    }

    public static void play(World world, Vec3d origin, float width, float yaw, float pitch, ParticleBatch batch) {
        try {
            var id = new Identifier(batch.particle_id);
            var particle = (ParticleEffect) Registry.PARTICLE_TYPE.get(id);
            var count = batch.count;
            var dynamicallyOffset = requiresDynamicOffset(batch);
            var defaultOrigin = origin.add(offset(width, batch.shape, batch.rotation, yaw, pitch));
            if (batch.count < 1) {
                count = rng.nextFloat() < batch.count ? 1 : 0;
            }
            for(int i = 0; i < count; ++i) {
                var direction = direction(batch, yaw, pitch);
                var particleSpecificOrigin = dynamicallyOffset ? origin.add(offset(width, batch.shape, batch.rotation, yaw, pitch)) : defaultOrigin;
                world.addParticle(particle, true,
                        particleSpecificOrigin.x, particleSpecificOrigin.y, particleSpecificOrigin.z,
                        direction.x, direction.y, direction.z);
            }
        } catch (Exception e) {
            System.err.println("Failed to play particle batch - " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static List<SpawnInstruction> convertToInstructions(World world, float pitch, float yaw, Packets.ParticleBatches packet) {
        var instructions = new ArrayList<SpawnInstruction>();
        var sourceType = packet.sourceType();
        for(var spawn: packet.spawns()) {
            var batch = spawn.batch();
            var origin = Vec3d.ZERO;
            float width = 0.5F;
            switch (sourceType) {
                case ENTITY -> {
                    var entity = world.getEntityById(spawn.sourceEntityId());
                    origin = origin(entity, batch.origin);
                }
                case COORDINATE -> {
                    origin = spawn.sourceLocation();
                }
            }

            var id = new Identifier(batch.particle_id);
            var particle = (ParticleEffect) Registry.PARTICLE_TYPE.get(id);
            var count = batch.count;
            var dynamicallyOffset = requiresDynamicOffset(batch);
            var defaultOrigin = origin.add(offset(width, batch.shape, batch.rotation, yaw, pitch));
            if (batch.count < 1) {
                count = rng.nextFloat() < batch.count ? 1 : 0;
            }
            for(int i = 0; i < count; ++i) {
                var direction = direction(batch, yaw, pitch);
                var particleSpecificOrigin = dynamicallyOffset ? origin.add(offset(width, batch.shape, batch.rotation, yaw, pitch)) : defaultOrigin;
                instructions.add(new SpawnInstruction(particle,
                        particleSpecificOrigin.x, particleSpecificOrigin.y, particleSpecificOrigin.z,
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
                return entity.getPos().add(0, entity.getHeight() * 0.5F, 0);
            }
            case LAUNCH_POINT -> {
                if (entity instanceof LivingEntity livingEntity) {
                    return SpellHelper.launchPoint(livingEntity, 0.15F);
                } else {
                    return entity.getPos().add(0, entity.getHeight() * 0.5F, 0);
                }
            }
        }
        assert true;
        return entity.getPos();
    }

    private static boolean requiresDynamicOffset(ParticleBatch origin) {
        switch (origin.shape) {
            case CIRCLE, CONE, SPHERE -> {
                return false;
            }
            case PILLAR, PIPE -> {
                // Volumetric spawn points
                return true;
            }
        }
        assert true;
        return false;
    }

    private static Vec3d offset(float width, ParticleBatch.Shape shape, ParticleBatch.Rotation rotation, float yaw, float pitch) {
        var offset = Vec3d.ZERO;
        switch (shape) {
            case CIRCLE, CONE, SPHERE -> {
                return offset;
            }
            case PIPE -> {
                var angle = (float) Math.toRadians(rng.nextFloat() * 360F);
                offset = new Vec3d(width,0,0).rotateY(angle);
            }
            case PILLAR -> {
                var x = randomInRange(0, width);
                var angle = (float) Math.toRadians(rng.nextFloat() * 360F);
                offset = new Vec3d(x,0,0).rotateY(angle);
            }
        }

        if (rotation != null) {
            switch (rotation) {
                case LOOK -> {
                    offset = offset
                            .rotateX((float) Math.toRadians(-1 * (pitch + 90)))
                            .rotateY((float) Math.toRadians(-yaw));
                }
            }
        }
        return offset;
    }

    private static Vec3d direction(ParticleBatch batch, float yaw, float pitch) {
        var direction = Vec3d.ZERO;
        float normalizedYaw = yaw % 360;
        float normalizedPitch = pitch % 360;

        float rotateAroundX = 0;
        float rotateAroundY = 0;
        switch (batch.shape) {
            case CONE -> {
                direction = new Vec3d(0, randomInRange(batch.min_speed, batch.max_speed), 0);
                rotateAroundX += rng.nextFloat() * batch.angle - (batch.angle * 0.5F);
                rotateAroundY += rng.nextFloat() * batch.angle - (batch.angle * 0.5F);
            }
            case CIRCLE -> {
                direction = new Vec3d(0, 0, randomInRange(batch.min_speed, batch.max_speed))
                        .rotateY((float) Math.toRadians(rng.nextFloat() * 360F));
            }
            case PILLAR, PIPE -> {
                direction = new Vec3d(0, randomInRange(batch.min_speed, batch.max_speed), 0);
            }
            case SPHERE -> {
                direction = new Vec3d(randomInRange(batch.min_speed, batch.max_speed), 0, 0)
                        .rotateZ((float) Math.toRadians(rng.nextFloat() * 360F))
                        .rotateY((float) Math.toRadians(rng.nextFloat() * 360F));
            }
        }
        if (batch.rotation != null) {
            switch (batch.rotation) {
                case LOOK -> {
                    rotateAroundX += normalizedPitch + 90;
                    rotateAroundY += normalizedYaw;
                }
            }
            direction = direction
                    .rotateX((float) Math.toRadians(rotateAroundX) * (-1F))
                    .rotateY((float) Math.toRadians(rotateAroundY) * (-1F));
        }

        return direction;
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
