package net.spell_engine.fx;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.spell_engine.api.spell.fx.ParticleBatch;
import net.spell_engine.client.particle.TemplateParticleEffect;
import net.spell_engine.client.util.Color;
import net.spell_engine.internals.SpellHelper;
import net.spell_engine.network.Packets;
import net.spell_engine.utils.TargetHelper;
import net.spell_engine.utils.VectorHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

public class ParticleHelper {
    private static Random rng = new Random();

    public static void sendBatches(Entity trackedEntity, ParticleBatch[] batches) {
        sendBatches(trackedEntity, batches, true);
    }

    public static void sendBatches(Entity trackedEntity, ParticleBatch[] batches, boolean includeSourceEntity) {
        sendBatches(trackedEntity, null, batches, 1, PlayerLookup.tracking(trackedEntity), includeSourceEntity);
    }

    public static void sendBatches(Entity trackedEntity, ParticleBatch[] batches, float countMultiplier, Collection<ServerPlayerEntity> trackers) {
        sendBatches(trackedEntity, null, batches, countMultiplier, trackers, true);
    }

    public static void sendBatches(Vec3d location, LivingEntity caster, ParticleBatch[] batches) {
        Collection<ServerPlayerEntity> trackers;
        if (caster instanceof ServerPlayerEntity serverPlayer) {
            var array = new ArrayList<ServerPlayerEntity>(PlayerLookup.tracking(caster));
            array.add(serverPlayer);
            trackers = array;
        } else {
            trackers = PlayerLookup.tracking(caster);
        }
        sendBatches(null, location, batches, 1, trackers, false);
    }

    public static void sendBatches(@Nullable Entity trackedEntity, @Nullable Vec3d location, ParticleBatch[] batches, float countMultiplier, Collection<ServerPlayerEntity> trackers, boolean includeSourceEntity) {
        if (batches == null || batches.length == 0) {
            return;
        }
        int sourceEntityId = 0;
        var sourceType = Packets.ParticleBatches.SourceType.COORDINATE;
        if (trackedEntity != null) {
            sourceEntityId = trackedEntity.getId();
            sourceType = Packets.ParticleBatches.SourceType.ENTITY;
        }
        ArrayList<Packets.ParticleBatches.Spawn> spawns = new ArrayList<>();
        for(var batch : batches) {
            Vec3d sourceLocation = Vec3d.ZERO;
            switch (sourceType) {
                case ENTITY -> {
                    sourceLocation = origin(trackedEntity, batch.origin);
                }
                case COORDINATE -> {
                    if (location != null) {
                        sourceLocation = location;
                    }
                }
            }
            float yaw = 0;
            float pitch = 0;
            if (trackedEntity != null) {
                yaw = trackedEntity.getYaw();
                pitch = trackedEntity.getPitch();
            }
            spawns.add(new Packets.ParticleBatches.Spawn(
                    includeSourceEntity ? sourceEntityId : 0,
                    yaw,
                    pitch,
                    sourceLocation, batch));
        }
        var packet = new Packets.ParticleBatches(sourceType, countMultiplier, spawns);
        if (trackedEntity instanceof ServerPlayerEntity serverPlayer) {
            if (ServerPlayNetworking.canSend(serverPlayer, Packets.ParticleBatches.ID)) {
                ServerPlayNetworking.send(serverPlayer, packet);
            }
        }
        trackers.forEach(serverPlayer -> {
            if (ServerPlayNetworking.canSend(serverPlayer, Packets.ParticleBatches.ID)) {
                ServerPlayNetworking.send(serverPlayer, packet);
            }
        });
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
        play(world, entity.age, origin(entity, batch.origin), entity.getWidth(), yaw, pitch, batch, entity);
    }

    private static ParticleEffect resolveParticleType(ParticleBatch batch, @Nullable Entity sourceEntity) {
        var id = Identifier.of(batch.particle_id);
        var particle = (ParticleEffect) Registries.PARTICLE_TYPE.get(id);

        if (particle instanceof TemplateParticleEffect templateParticleEffect) {
            var copy = templateParticleEffect.copy();
            var appearance = copy.createOrDefaultAppearance();
            if (batch.color_rgba >= 0) {
                appearance.color = Color.fromRGBA(batch.color_rgba);
            }
            if (batch.follow_entity) {
                appearance.entityFollowed = sourceEntity;
            }
            if (batch.scale != 1) {
                appearance.scale = batch.scale;
            }
            if (batch.origin == ParticleBatch.Origin.GROUND) {
                appearance.grounded = true;
            }
            appearance.max_age = batch.max_age;
            particle = copy;
        }
        return particle;
    }

    public static void play(World world, long time, Vec3d origin, float width, float yaw, float pitch, ParticleBatch batch, @Nullable Entity sourceEntity) {
        try {
            var particle = resolveParticleType(batch, sourceEntity);

            var count = batch.count;
            if (batch.count < 1) {
                count = rng.nextFloat() < batch.count ? 1 : 0;
            }
            for(int i = 0; i < count; ++i) {
                var direction = direction(batch, time, yaw, pitch);
                var particleSpecificOrigin = origin.add(offset(width, batch.extent, batch.shape, direction.normalize(), batch.rotation, yaw, pitch));
                if (batch.pre_spawn_travel != 0) {
                    particleSpecificOrigin = particleSpecificOrigin.add(direction.multiply(batch.pre_spawn_travel));
                }
                if (batch.invert) {
                    direction = direction.negate();
                }
                world.addParticle(particle, true,
                        particleSpecificOrigin.x, particleSpecificOrigin.y, particleSpecificOrigin.z,
                        direction.x, direction.y, direction.z);
            }
        } catch (Exception e) {
            System.err.println("Failed to play particle batch - " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static List<SpawnInstruction> convertToInstructions(World world, Packets.ParticleBatches packet) {
        var instructions = new ArrayList<SpawnInstruction>();
        var sourceType = packet.sourceType();
        for(var spawn: packet.spawns()) {
            float yaw = spawn.yaw();
            float pitch = spawn.pitch();
            var batch = spawn.batch();
            var origin = Vec3d.ZERO;
            float width = 0.5F;
            Entity sourceEntity = world.getEntityById(spawn.sourceEntityId());
            switch (sourceType) {
                case ENTITY -> {
                    origin = sourceEntity != null
                            ? origin(sourceEntity, batch.origin)
                            : origin(world, spawn.sourceLocation(), 2, batch.origin);
                }
                case COORDINATE -> {
                    origin = spawn.sourceLocation();
                }
            }

            var particle = resolveParticleType(batch, sourceEntity);

            var count = batch.count;
            if (batch.count < 1) {
                count = rng.nextFloat() < batch.count ? 1 : 0;
            }
            for(int i = 0; i < count; ++i) {
                var direction = direction(batch, world.getTime(), yaw, pitch);
                var particleSpecificOrigin = origin.add(offset(width, batch.extent, batch.shape, direction.normalize(), batch.rotation, yaw, pitch));
                if (batch.pre_spawn_travel != 0) {
                    particleSpecificOrigin = particleSpecificOrigin.add(direction.multiply(batch.pre_spawn_travel));
                }
                if (batch.invert) {
                    direction = direction.negate();
                }
                instructions.add(new SpawnInstruction(particle, sourceEntity,
                        particleSpecificOrigin.x, particleSpecificOrigin.y, particleSpecificOrigin.z,
                        direction.x, direction.y, direction.z));
            }
        }
        return instructions;
    }

    public record SpawnInstruction(ParticleEffect particle, @Nullable Entity sourceEntity,
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
                    return SpellHelper.launchPoint(livingEntity);
                } else {
                    return entity.getPos().add(0, entity.getHeight() * 0.5F, 0);
                }
            }
            case GROUND -> {
                var position = TargetHelper.findSolidBelow(entity, entity.getPos(), entity.getWorld(), -2);
                if (position != null) {
                    return new Vec3d(entity.getX(), position.getY() + 0.1F, entity.getZ());
                } else {
                    return entity.getPos().add(0, 0.1F, 0);
                }
            }
        }
        assert true;
        return entity.getPos();
    }

    private static Vec3d origin(World world, Vec3d entityPos, float entityHeight, ParticleBatch.Origin origin) {
        switch (origin) {
            case FEET -> {
                return entityPos.add(0, entityHeight * 0.1F, 0);
            }
            case CENTER -> {
                return entityPos.add(0, entityHeight * 0.5F, 0);
            }
            case LAUNCH_POINT -> {
                return entityPos.add(0, entityHeight * 0.75F, 0);
            }
            case GROUND -> {
                var position = TargetHelper.findSolidBlockBelow(null, entityPos, world, -2);
                if (position != null) {
                    return new Vec3d(entityPos.getX(), position.getY() + 0.1F, entityPos.getZ());
                } else {
                    return entityPos.add(0, 0.1F, 0);
                }
            }
        }
        assert true;
        return entityPos;
    }

    private static Vec3d offset(float width, float extent, ParticleBatch.Shape shape, Vec3d direction,
                                ParticleBatch.Rotation rotation, float yaw, float pitch) {
        var offset = Vec3d.ZERO;
        if (extent >= ParticleBatch.EXTENT_TRESHOLD) {
            width = 0;
            extent -= ParticleBatch.EXTENT_TRESHOLD;
        }
        switch (shape) {
            case LINE_VERTICAL, CIRCLE, CONE, SPHERE -> {
                if (extent > 0) {
                    offset = direction.multiply(extent);
                }
                return offset;
            }
            case PIPE -> {
                var size = width * 0.5F + extent;
                var angle = (float) Math.toRadians(rng.nextFloat() * 360F);
                offset = new Vec3d(size,0,0).rotateY(angle);
            }
            case WIDE_PIPE -> {
                var size = width + extent;
                var angle = (float) Math.toRadians(rng.nextFloat() * 360F);
                offset = new Vec3d(size,0,0).rotateY(angle);
            }
            case PILLAR -> {
                var x = (width * 0.5F + extent) * rng.nextFloat();
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

    private static Vec3d direction(ParticleBatch batch, long time, float yaw, float pitch) {
        var direction = Vec3d.ZERO;

        float rotateAroundX = 0;
        float rotateAroundY = 0;
        switch (batch.shape) {
            case LINE -> {
                direction = new Vec3d(0, 0, randomInRange(batch.min_speed, batch.max_speed));
                pitch = -pitch; // Inverting pitch, do not remove, it makes things work :D
            }
            case CONE -> {
                direction = new Vec3d(0, randomInRange(batch.min_speed, batch.max_speed), 0);
                rotateAroundX += rng.nextFloat() * batch.angle - (batch.angle * 0.5F);
                rotateAroundY += rng.nextFloat() * batch.angle - (batch.angle * 0.5F);
            }
            case CIRCLE -> {
                direction = new Vec3d(0, 0, randomInRange(batch.min_speed, batch.max_speed))
                        .rotateY((float) Math.toRadians(rng.nextFloat() * 360F));
            }
            case LINE_VERTICAL, PILLAR, PIPE, WIDE_PIPE -> {
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
                    // Find actual rotation
                    float pRot = -pitch;
                    float yRot = yaw * (-1F);

                    direction = direction
                            .rotateX((float) Math.toRadians(pRot - 90 + rotateAroundX))
                            .rotateY((float) Math.toRadians(yRot + rotateAroundY));

                    if (batch.roll > 0) {
                        var axis = VectorHelper.axisFromRotation(yRot, pRot).negate();
                        var diff = ((time * batch.roll) % 360) + batch.roll_offset;
                        direction = VectorHelper.rotateAround(direction, axis, diff);
                    }
                }
            }
        } else {
            direction = direction
                    .rotateX((float) Math.toRadians(rotateAroundX))
                    .rotateY((float) Math.toRadians(rotateAroundY));

            if (batch.roll > 0) {
                var diff = ((time * batch.roll) % 360) + batch.roll_offset;
                direction = direction.rotateY((float) Math.toRadians(diff));
            }
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
