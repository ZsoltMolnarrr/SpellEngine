package net.spell_engine.fx;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.spell_engine.Platform;
import net.spell_engine.api.spell.fx.ParticleGroup;
import net.spell_engine.network.Packets;
import net.spell_engine.utils.TargetHelper;
import net.spell_engine.utils.VectorHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import net.spell_engine.internals.delivery.LaunchGeometry;

/// Spawns and broadcasts [ParticleGroup]s.
///
/// The [ParticleGroup.Batch] block is resolved here — placement, count and
/// initial velocity — while the [ParticleGroup.Appearance] block travels
/// untouched to the client factory. One geometry core serves both the local
/// spawn path ([#play]) and the network receive path ([#convertToInstructions]).
public class ParticleHelper {
    private static final Random rng = new Random();

    // MARK: - Server → client broadcasting

    public static void sendBatches(Entity trackedEntity, List<ParticleGroup> effects) {
        sendBatches(trackedEntity, effects, true);
    }

    public static void sendBatches(Entity trackedEntity, List<ParticleGroup> effects, boolean includeSourceEntity) {
        sendBatches(trackedEntity, null, effects, 1, Platform.tracking(trackedEntity), includeSourceEntity);
    }

    public static void sendBatches(Entity trackedEntity, List<ParticleGroup> effects, float countMultiplier, Collection<ServerPlayer> trackers) {
        sendBatches(trackedEntity, null, effects, countMultiplier, trackers, true);
    }

    public static void sendBatches(Vec3 location, LivingEntity caster, List<ParticleGroup> effects) {
        Collection<ServerPlayer> trackers;
        if (caster instanceof ServerPlayer serverPlayer) {
            var array = new ArrayList<ServerPlayer>(Platform.tracking(caster));
            array.add(serverPlayer);
            trackers = array;
        } else {
            trackers = Platform.tracking(caster);
        }
        sendBatches(null, location, effects, 1, trackers, false);
    }

    /// Like {@link #sendBatches(Entity, List)}, but anchors the packet to the entity's
    /// CURRENT server-side position (COORDINATE source) instead of the entity id. Use for one-shot
    /// FX of entities that may die in the same tick (e.g. a falling projectile's impact burst):
    /// an entity-anchored packet races the source entity's client-side simulation and removal —
    /// the receiving client prefers its local entity position, which for a dying projectile can
    /// be several blocks past the server's impact point.
    public static void sendBatchesDetached(Entity sourceEntity, List<ParticleGroup> effects) {
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
                    sourceEntity.getYRot(),
                    sourceEntity.getXRot(),
                    origin(sourceEntity, effect.batch), effect));
        }
        var packet = new Packets.ParticleEffects(Packets.ParticleEffects.SourceType.COORDINATE, 1, spawns);
        if (sourceEntity instanceof ServerPlayer serverPlayer) {
            if (Platform.util().networkS2C_CanSend(serverPlayer, Packets.ParticleEffects.ID)) {
                Platform.util().networkS2C_Send(serverPlayer, packet);
            }
        }
        Platform.tracking(sourceEntity).forEach(serverPlayer -> {
            if (Platform.util().networkS2C_CanSend(serverPlayer, Packets.ParticleEffects.ID)) {
                Platform.util().networkS2C_Send(serverPlayer, packet);
            }
        });
    }

    public static void sendBatches(@Nullable Entity trackedEntity, @Nullable Vec3 location, List<ParticleGroup> effects, float countMultiplier, Collection<ServerPlayer> trackers, boolean includeSourceEntity) {
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
            Vec3 sourceLocation = Vec3.ZERO;
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
                yaw = trackedEntity.getYRot();
                pitch = trackedEntity.getXRot();
            }
            spawns.add(new Packets.ParticleEffects.Spawn(
                    includeSourceEntity ? sourceEntityId : 0,
                    yaw,
                    pitch,
                    sourceLocation, effect));
        }
        var packet = new Packets.ParticleEffects(sourceType, countMultiplier, spawns);
        if (trackedEntity instanceof ServerPlayer serverPlayer) {
            if (Platform.util().networkS2C_CanSend(serverPlayer, Packets.ParticleEffects.ID)) {
                Platform.util().networkS2C_Send(serverPlayer, packet);
            }
        }
        trackers.forEach(serverPlayer -> {
            if (Platform.util().networkS2C_CanSend(serverPlayer, Packets.ParticleEffects.ID)) {
                Platform.util().networkS2C_Send(serverPlayer, packet);
            }
        });
    }

    // MARK: - Local (client-side) spawning

    public static void play(Level world, Entity source, List<ParticleGroup> effects) {
        if (effects == null) {
            return;
        }
        for (var effect : effects) {
            play(world, source, 0, 0, effect);
        }
    }

    public static void play(Level world, Entity source, ParticleGroup effect) {
        play(world, source, 0, 0, effect);
    }

    public static void play(Level world, Entity entity, float yaw, float pitch, ParticleGroup effect) {
        play(world, entity.tickCount, origin(entity, effect.batch), entity.getBbWidth(), yaw, pitch, effect, entity);
    }

    public static void play(Level world, long time, Vec3 origin, float width, float yaw, float pitch, ParticleGroup effect, @Nullable Entity sourceEntity) {
        try {
            var instructions = new ArrayList<SpawnInstruction>();
            emit(time, origin, width, yaw, pitch, effect, 1F, true, sourceEntity, instructions);
            for (var instruction : instructions) {
                instruction.perform(world);
            }
        } catch (Exception e) {
            System.err.println("Failed to play particle effect - " + e.getMessage());
            e.printStackTrace();
        }
    }

    // MARK: - Network receive path

    public static List<SpawnInstruction> convertToInstructions(Level world, Packets.ParticleEffects packet) {
        var instructions = new ArrayList<SpawnInstruction>();
        var sourceType = packet.sourceType();
        for (var spawn : packet.spawns()) {
            var effect = spawn.effect();
            var origin = Vec3.ZERO;
            float width = 0.5F;
            Entity sourceEntity = world.getEntity(spawn.sourceEntityId());
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
            emit(world.getGameTime(), origin, width, spawn.yaw(), spawn.pitch(), effect, packet.countMultiplier(), false, sourceEntity, instructions);
        }
        return instructions;
    }

    public record SpawnInstruction(ParticleOptions particle,
                                   double positionX, double positionY, double positionZ,
                                   double velocityX, double velocityY, double velocityZ) {
        public void perform(Level world) {
            try {
                world.addAlwaysVisibleParticle(particle,
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
    /// `continuous` marks the per-tick path (casting, cloud ambience, trails), where a
    /// fractional [ParticleGroup.Batch#count] means "one every N ticks". One-shot emissions
    /// pass `false`: they happen at an instant, with no next tick to skip to.
    private static void emit(long time, Vec3 origin, float width, float yaw, float pitch,
                             ParticleGroup effect, float countMultiplier, boolean continuous,
                             @Nullable Entity sourceEntity, List<SpawnInstruction> output) {
        var registryEntry = BuiltInRegistries.PARTICLE_TYPE.getValue(Identifier.parse(effect.id));
        if (registryEntry == null) {
            return;
        }
        var particle = (ParticleOptions) registryEntry;
        if (particle instanceof ParticleGroupType groupType) {
            particle = groupType.spawnable(effect.appearance, sourceEntity);
        }

        var batch = effect.batch;
        if (batch.chance < 1F && rng.nextFloat() >= batch.chance) {
            return;
        }
        float count;
        if (batch.count < 1F) {
            // A period, not a count. Only meaningful on the continuous path — a one-shot
            // has no next tick to wait for, so it just emits.
            if (batch.count <= 0F) {
                return;
            }
            if (continuous) {
                var period = Math.max(1, Math.round(1F / batch.count));
                if (Math.floorMod(time, period) != 0) {
                    return;
                }
            }
            count = 1;
        } else {
            // The multiplier scales a count; it has no meaning against a period.
            count = batch.count * countMultiplier;
        }
        for (int i = 0; i < count; ++i) {
            var direction = direction(batch, time, yaw, pitch);
            var particleSpecificOrigin = origin.add(offset(width, batch, direction.normalize(), yaw, pitch));
            if (batch.pre_travel != 0) {
                particleSpecificOrigin = particleSpecificOrigin.add(direction.scale(batch.pre_travel));
            }
            if (batch.invert) {
                direction = direction.reverse();
            }
            output.add(new SpawnInstruction(particle,
                    particleSpecificOrigin.x, particleSpecificOrigin.y, particleSpecificOrigin.z,
                    direction.x, direction.y, direction.z));
        }
    }

    private static Vec3 origin(Entity entity, ParticleGroup.Batch batch) {
        switch (batch.anchor) {
            case ENTITY -> {
                return entity.position().add(0, entity.getBbHeight() * batch.vertical_origin, 0);
            }
            case LAUNCH_POINT -> {
                if (entity instanceof LivingEntity livingEntity) {
                    return LaunchGeometry.launchPoint(livingEntity);
                } else {
                    return entity.position().add(0, entity.getBbHeight() * 0.5F, 0);
                }
            }
            case GROUND -> {
                var position = TargetHelper.findSolidBelow(entity, entity.position(), entity.level(), -2);
                if (position != null) {
                    return new Vec3(entity.getX(), position.y() + 0.1F, entity.getZ());
                } else {
                    return entity.position().add(0, 0.1F, 0);
                }
            }
        }
        return entity.position();
    }

    private static Vec3 origin(Level world, Vec3 entityPos, float entityHeight, ParticleGroup.Batch batch) {
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
                    return new Vec3(entityPos.x(), position.y() + 0.1F, entityPos.z());
                } else {
                    return entityPos.add(0, 0.1F, 0);
                }
            }
        }
        return entityPos;
    }

    private static Vec3 offset(float width, ParticleGroup.Batch batch, Vec3 direction, float yaw, float pitch) {
        var offset = Vec3.ZERO;
        // `width_factor` scales the entity's contribution; `0` makes `extent` absolute
        // (replaces the V1 EXTENT_TRESHOLD sentinel), `2` reproduces V1 WIDE_PIPE.
        var radius = width * 0.5F * batch.width_factor;
        switch (batch.shape) {
            case NONE -> {
                return Vec3.ZERO;
            }
            case LINE_VERTICAL, CIRCLE, CONE, SPHERE -> {
                if (batch.extent > 0) {
                    offset = direction.scale(batch.extent);
                }
                return offset;
            }
            case PIPE -> {
                var size = radius + batch.extent;
                var angle = (float) Math.toRadians(rng.nextFloat() * 360F);
                offset = new Vec3(size, 0, 0).yRot(angle);
            }
            case PILLAR -> {
                var x = (radius + batch.extent) * rng.nextFloat();
                var angle = (float) Math.toRadians(rng.nextFloat() * 360F);
                offset = new Vec3(x, 0, 0).yRot(angle);
            }
            case LINE -> {
                return offset;
            }
        }

        if (batch.alignment == ParticleGroup.Alignment.LOOK) {
            offset = offset
                    .xRot((float) Math.toRadians(-1 * (pitch + 90)))
                    .yRot((float) Math.toRadians(-yaw));
        }
        return offset;
    }

    private static Vec3 direction(ParticleGroup.Batch batch, long time, float yaw, float pitch) {
        var direction = Vec3.ZERO;

        float rotateAroundX = 0;
        float rotateAroundY = 0;
        switch (batch.shape) {
            case NONE -> {
                // Placed, not thrown: no velocity, and nothing for roll/pre_travel to act on
                return Vec3.ZERO;
            }
            case LINE -> {
                direction = new Vec3(0, 0, randomInRange(batch.min_speed, batch.max_speed));
                pitch = -pitch; // Inverting pitch, do not remove, it makes things work :D
            }
            case CONE -> {
                direction = new Vec3(0, randomInRange(batch.min_speed, batch.max_speed), 0);
                rotateAroundX += rng.nextFloat() * batch.angle - (batch.angle * 0.5F);
                rotateAroundY += rng.nextFloat() * batch.angle - (batch.angle * 0.5F);
            }
            case CIRCLE -> {
                direction = new Vec3(0, 0, randomInRange(batch.min_speed, batch.max_speed))
                        .yRot((float) Math.toRadians(rng.nextFloat() * 360F));
            }
            case LINE_VERTICAL, PILLAR, PIPE -> {
                direction = new Vec3(0, randomInRange(batch.min_speed, batch.max_speed), 0);
            }
            case SPHERE -> {
                direction = new Vec3(randomInRange(batch.min_speed, batch.max_speed), 0, 0)
                        .zRot((float) Math.toRadians(rng.nextFloat() * 360F))
                        .yRot((float) Math.toRadians(rng.nextFloat() * 360F));
            }
        }
        if (batch.alignment == ParticleGroup.Alignment.LOOK) {
            // Find actual rotation
            float pRot = -pitch;
            float yRot = yaw * (-1F);

            direction = direction
                    .xRot((float) Math.toRadians(pRot - 90 + rotateAroundX))
                    .yRot((float) Math.toRadians(yRot + rotateAroundY));

            if (batch.roll_per_tick != 0) {
                var axis = VectorHelper.axisFromRotation(yRot, pRot).reverse();
                var diff = ((time * batch.roll_per_tick) % 360) + batch.roll_offset;
                direction = VectorHelper.rotateAround(direction, axis, diff);
            }
        } else {
            direction = direction
                    .xRot((float) Math.toRadians(rotateAroundX))
                    .yRot((float) Math.toRadians(rotateAroundY));

            if (batch.roll_per_tick != 0) {
                var diff = ((time * batch.roll_per_tick) % 360) + batch.roll_offset;
                direction = direction.yRot((float) Math.toRadians(diff));
            }
        }

        return direction;
    }

    private static float randomInRange(float min, float max) {
        float range = max - min;
        return min + (range * rng.nextFloat());
    }
}
