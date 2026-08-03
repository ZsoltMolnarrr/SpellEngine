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
import net.spell_engine.api.spell.fx.ParticleGroupEffect;
import net.spell_engine.internals.SpellHelper;
import net.spell_engine.network.Packets;
import net.spell_engine.utils.TargetHelper;
import net.spell_engine.utils.VectorHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

/// Spawns and broadcasts [ParticleGroupEffect]s.
///
/// The [ParticleGroupEffect.Batch] block is resolved here — placement, count and
/// initial velocity — while the [ParticleGroupEffect.Particle] block travels
/// untouched to the client factory. One geometry core serves both the local
/// spawn path ([#play]) and the network receive path ([#convertToInstructions]).
public class ParticleHelper {
    private static final Random rng = new Random();

    // MARK: - Server → client broadcasting

    public static void sendBatches(Entity trackedEntity, List<ParticleGroupEffect> effects) {
        sendBatches(trackedEntity, effects, true);
    }

    public static void sendBatches(Entity trackedEntity, List<ParticleGroupEffect> effects, boolean includeSourceEntity) {
        sendBatches(trackedEntity, null, effects, 1, PlayerLookup.tracking(trackedEntity), includeSourceEntity);
    }

    public static void sendBatches(Entity trackedEntity, List<ParticleGroupEffect> effects, float countMultiplier, Collection<ServerPlayerEntity> trackers) {
        sendBatches(trackedEntity, null, effects, countMultiplier, trackers, true);
    }

    public static void sendBatches(Vec3d location, LivingEntity caster, List<ParticleGroupEffect> effects) {
        Collection<ServerPlayerEntity> trackers;
        if (caster instanceof ServerPlayerEntity serverPlayer) {
            var array = new ArrayList<ServerPlayerEntity>(PlayerLookup.tracking(caster));
            array.add(serverPlayer);
            trackers = array;
        } else {
            trackers = PlayerLookup.tracking(caster);
        }
        sendBatches(null, location, effects, 1, trackers, false);
    }

    /// Like {@link #sendBatches(Entity, List)}, but anchors the packet to the entity's
    /// CURRENT server-side position (COORDINATE source) instead of the entity id. Use for one-shot
    /// FX of entities that may die in the same tick (e.g. a falling projectile's impact burst):
    /// an entity-anchored packet races the source entity's client-side simulation and removal —
    /// the receiving client prefers its local entity position, which for a dying projectile can
    /// be several blocks past the server's impact point.
    public static void sendBatchesDetached(Entity sourceEntity, List<ParticleGroupEffect> effects) {
        if (effects == null || effects.isEmpty()) {
            return;
        }
        ArrayList<Packets.ParticleEffects.Spawn> spawns = new ArrayList<>();
        for (var effect : effects) {
            // Origins and orientation resolved server-side, exactly like the ENTITY source type.
            // The entity id is still included so entity-bound particle resolution keeps working
            // when the entity is present.
            spawns.add(new Packets.ParticleEffects.Spawn(
                    sourceEntity.getId(),
                    sourceEntity.getYaw(),
                    sourceEntity.getPitch(),
                    origin(sourceEntity, effect.batch), effect));
        }
        var packet = new Packets.ParticleEffects(Packets.ParticleEffects.SourceType.COORDINATE, 1, spawns);
        if (sourceEntity instanceof ServerPlayerEntity serverPlayer) {
            if (ServerPlayNetworking.canSend(serverPlayer, Packets.ParticleEffects.ID)) {
                ServerPlayNetworking.send(serverPlayer, packet);
            }
        }
        PlayerLookup.tracking(sourceEntity).forEach(serverPlayer -> {
            if (ServerPlayNetworking.canSend(serverPlayer, Packets.ParticleEffects.ID)) {
                ServerPlayNetworking.send(serverPlayer, packet);
            }
        });
    }

    public static void sendBatches(@Nullable Entity trackedEntity, @Nullable Vec3d location, List<ParticleGroupEffect> effects, float countMultiplier, Collection<ServerPlayerEntity> trackers, boolean includeSourceEntity) {
        if (effects == null || effects.isEmpty()) {
            return;
        }
        int sourceEntityId = 0;
        var sourceType = Packets.ParticleEffects.SourceType.COORDINATE;
        if (trackedEntity != null) {
            sourceEntityId = trackedEntity.getId();
            sourceType = Packets.ParticleEffects.SourceType.ENTITY;
        }
        ArrayList<Packets.ParticleEffects.Spawn> spawns = new ArrayList<>();
        for (var effect : effects) {
            Vec3d sourceLocation = Vec3d.ZERO;
            switch (sourceType) {
                case ENTITY -> {
                    sourceLocation = origin(trackedEntity, effect.batch);
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
            spawns.add(new Packets.ParticleEffects.Spawn(
                    includeSourceEntity ? sourceEntityId : 0,
                    yaw,
                    pitch,
                    sourceLocation, effect));
        }
        var packet = new Packets.ParticleEffects(sourceType, countMultiplier, spawns);
        if (trackedEntity instanceof ServerPlayerEntity serverPlayer) {
            if (ServerPlayNetworking.canSend(serverPlayer, Packets.ParticleEffects.ID)) {
                ServerPlayNetworking.send(serverPlayer, packet);
            }
        }
        trackers.forEach(serverPlayer -> {
            if (ServerPlayNetworking.canSend(serverPlayer, Packets.ParticleEffects.ID)) {
                ServerPlayNetworking.send(serverPlayer, packet);
            }
        });
    }

    // MARK: - Local (client-side) spawning

    public static void play(World world, Entity source, List<ParticleGroupEffect> effects) {
        if (effects == null) {
            return;
        }
        for (var effect : effects) {
            play(world, source, 0, 0, effect);
        }
    }

    public static void play(World world, Entity source, ParticleGroupEffect effect) {
        play(world, source, 0, 0, effect);
    }

    public static void play(World world, Entity entity, float yaw, float pitch, ParticleGroupEffect effect) {
        play(world, entity.age, origin(entity, effect.batch), entity.getWidth(), yaw, pitch, effect, entity);
    }

    public static void play(World world, long time, Vec3d origin, float width, float yaw, float pitch, ParticleGroupEffect effect, @Nullable Entity sourceEntity) {
        try {
            var instructions = new ArrayList<SpawnInstruction>();
            emit(time, origin, width, yaw, pitch, effect, 1F, sourceEntity, instructions);
            for (var instruction : instructions) {
                instruction.perform(world);
            }
        } catch (Exception e) {
            System.err.println("Failed to play particle effect - " + e.getMessage());
            e.printStackTrace();
        }
    }

    // MARK: - Network receive path

    public static List<SpawnInstruction> convertToInstructions(World world, Packets.ParticleEffects packet) {
        var instructions = new ArrayList<SpawnInstruction>();
        var sourceType = packet.sourceType();
        for (var spawn : packet.spawns()) {
            var effect = spawn.effect();
            var origin = Vec3d.ZERO;
            float width = 0.5F;
            Entity sourceEntity = world.getEntityById(spawn.sourceEntityId());
            switch (sourceType) {
                case ENTITY -> {
                    origin = sourceEntity != null
                            ? origin(sourceEntity, effect.batch)
                            : origin(world, spawn.sourceLocation(), 2, effect.batch);
                }
                case COORDINATE -> {
                    origin = spawn.sourceLocation();
                }
            }
            emit(world.getTime(), origin, width, spawn.yaw(), spawn.pitch(), effect, packet.countMultiplier(), sourceEntity, instructions);
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

    // MARK: - Geometry core

    /// Resolves one effect's batch into spawn instructions.
    /// Shared by the local and the network path.
    private static void emit(long time, Vec3d origin, float width, float yaw, float pitch,
                             ParticleGroupEffect effect, float countMultiplier,
                             @Nullable Entity sourceEntity, List<SpawnInstruction> output) {
        var registryEntry = Registries.PARTICLE_TYPE.get(Identifier.of(effect.id));
        if (registryEntry == null) {
            return;
        }
        var particle = (ParticleEffect) registryEntry;
        if (particle instanceof ParticleGroupEffectType groupType) {
            particle = groupType.spawnable(effect.particle, sourceEntity);
        }

        var batch = effect.batch;
        var count = batch.count * countMultiplier;
        if (count < 1) {
            count = rng.nextFloat() < count ? 1 : 0;
        }
        for (int i = 0; i < count; ++i) {
            var direction = direction(batch, time, yaw, pitch);
            var particleSpecificOrigin = origin.add(offset(width, batch, direction.normalize(), yaw, pitch));
            if (batch.pre_travel != 0) {
                particleSpecificOrigin = particleSpecificOrigin.add(direction.multiply(batch.pre_travel));
            }
            if (batch.invert) {
                direction = direction.negate();
            }
            output.add(new SpawnInstruction(particle,
                    particleSpecificOrigin.x, particleSpecificOrigin.y, particleSpecificOrigin.z,
                    direction.x, direction.y, direction.z));
        }
    }

    private static Vec3d origin(Entity entity, ParticleGroupEffect.Batch batch) {
        switch (batch.anchor) {
            case ENTITY -> {
                return entity.getPos().add(0, entity.getHeight() * batch.vertical_origin, 0);
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
        return entity.getPos();
    }

    private static Vec3d origin(World world, Vec3d entityPos, float entityHeight, ParticleGroupEffect.Batch batch) {
        switch (batch.anchor) {
            case ENTITY -> {
                return entityPos.add(0, entityHeight * batch.vertical_origin, 0);
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
        return entityPos;
    }

    private static Vec3d offset(float width, ParticleGroupEffect.Batch batch, Vec3d direction, float yaw, float pitch) {
        var offset = Vec3d.ZERO;
        // `width_factor` scales the entity's contribution; `0` makes `extent` absolute
        // (replaces the V1 EXTENT_TRESHOLD sentinel), `2` reproduces V1 WIDE_PIPE.
        var radius = width * 0.5F * batch.width_factor;
        switch (batch.shape) {
            case LINE_VERTICAL, CIRCLE, CONE, SPHERE -> {
                if (batch.extent > 0) {
                    offset = direction.multiply(batch.extent);
                }
                return offset;
            }
            case PIPE -> {
                var size = radius + batch.extent;
                var angle = (float) Math.toRadians(rng.nextFloat() * 360F);
                offset = new Vec3d(size, 0, 0).rotateY(angle);
            }
            case PILLAR -> {
                var x = (radius + batch.extent) * rng.nextFloat();
                var angle = (float) Math.toRadians(rng.nextFloat() * 360F);
                offset = new Vec3d(x, 0, 0).rotateY(angle);
            }
            case LINE -> {
                return offset;
            }
        }

        if (batch.alignment == ParticleGroupEffect.Alignment.LOOK) {
            offset = offset
                    .rotateX((float) Math.toRadians(-1 * (pitch + 90)))
                    .rotateY((float) Math.toRadians(-yaw));
        }
        return offset;
    }

    private static Vec3d direction(ParticleGroupEffect.Batch batch, long time, float yaw, float pitch) {
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
            case LINE_VERTICAL, PILLAR, PIPE -> {
                direction = new Vec3d(0, randomInRange(batch.min_speed, batch.max_speed), 0);
            }
            case SPHERE -> {
                direction = new Vec3d(randomInRange(batch.min_speed, batch.max_speed), 0, 0)
                        .rotateZ((float) Math.toRadians(rng.nextFloat() * 360F))
                        .rotateY((float) Math.toRadians(rng.nextFloat() * 360F));
            }
        }
        if (batch.alignment == ParticleGroupEffect.Alignment.LOOK) {
            // Find actual rotation
            float pRot = -pitch;
            float yRot = yaw * (-1F);

            direction = direction
                    .rotateX((float) Math.toRadians(pRot - 90 + rotateAroundX))
                    .rotateY((float) Math.toRadians(yRot + rotateAroundY));

            if (batch.roll_per_tick != 0) {
                var axis = VectorHelper.axisFromRotation(yRot, pRot).negate();
                var diff = ((time * batch.roll_per_tick) % 360) + batch.roll_offset;
                direction = VectorHelper.rotateAround(direction, axis, diff);
            }
        } else {
            direction = direction
                    .rotateX((float) Math.toRadians(rotateAroundX))
                    .rotateY((float) Math.toRadians(rotateAroundY));

            if (batch.roll_per_tick != 0) {
                var diff = ((time * batch.roll_per_tick) % 360) + batch.roll_offset;
                direction = direction.rotateY((float) Math.toRadians(diff));
            }
        }

        return direction;
    }

    private static float randomInRange(float min, float max) {
        float range = max - min;
        return min + (range * rng.nextFloat());
    }
}
