package net.spell_engine.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.internals.delivery.Beam;
import net.spell_engine.internals.casting.SpellCaster;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.spell_engine.internals.delivery.LaunchGeometry;

public class TargetHelper {

    public static Vec3 locationFromRayCast(Entity caster, float range) {
        Vec3 start = caster.getEyePosition();
        Vec3 look = caster.getViewVector(1.0F)
                .normalize()
                .scale(range);
        Vec3 end = start.add(look);
        var hit = raycastObstacle(caster.level(), caster, start, end);
        if (hit.getType() == HitResult.Type.BLOCK) {
            return hit.getLocation();
        }
        return end;
    }

    public static Entity targetFromRaycast(Entity caster, float range, Predicate<Entity> predicate) {
        Vec3 start = caster.getEyePosition();
        Vec3 look = caster.getViewVector(1.0F)
                .normalize()
                .scale(range);
        Vec3 end = start.add(look);
        AABB searchAABB = caster.getBoundingBox().inflate(range, range, range);
        var hitResult = ProjectileUtil.getEntityHitResult(caster, start, end, searchAABB, (target) -> {
            return !target.isSpectator() && target.isPickable() && predicate.test(target);
        }, range*range); // `range*range` is provided for squared distance comparison
        if (hitResult != null) {
            if (hitResult.getLocation() == null || raycastObstacleFree(caster.level(), caster, start, hitResult.getLocation())) {
                return hitResult.getEntity();
            }
        }
        return null;
    }

    public static List<Entity> targetsFromRaycast(Entity caster, float range, Predicate<Entity> predicate) {
        Vec3 start = caster.getEyePosition();
        Vec3 look = caster.getViewVector(1.0F)
                .normalize()
                .scale(range);
        Vec3 end = start.add(look);
        AABB searchAABB = caster.getBoundingBox().inflate(range, range, range);
        var entitiesHit = TargetHelper.raycastMultiple(caster, start, end, searchAABB, (target) -> {
            return !target.isSpectator() && target.isPickable() && predicate.test(target);
        }, range*range); // `range*range` is provided for squared distance comparison
        return entitiesHit.stream()
                .filter((hit) -> hit.position() == null || raycastObstacleFree(caster.level(), caster, start, hit.position()))
                .sorted(new Comparator<EntityHit>() {
                    @Override
                    public int compare(EntityHit hit1, EntityHit hit2) {
                        if (hit1.squaredDistanceToSource == hit2.squaredDistanceToSource) {
                            return 0;
                        }
                        return (hit1.squaredDistanceToSource < hit2.squaredDistanceToSource) ? -1 : 1;
                    }
                })
                .map(hit -> hit.entity)
                .toList();
    }

    private record EntityHit(Entity entity, Vec3 position, double squaredDistanceToSource) { }

    @Nullable
    private static List<EntityHit> raycastMultiple(Entity sourceEntity, Vec3 min, Vec3 max, AABB searchBox, Predicate<Entity> predicate, double squaredDistance) {
        Level world = sourceEntity.level();
        double e = squaredDistance;
        // Entity entity2 = null;
        List<EntityHit> entities = new ArrayList<>();
        Vec3 vec3d = null;
        for (Entity entity : world.getEntities(sourceEntity, searchBox, predicate)) {
            Vec3 hitPosition;
            double f;
            AABB box2 = entity.getBoundingBox().inflate(entity.getPickRadius());
            Optional<Vec3> raycastResult = box2.clip(min, max);
            if (box2.contains(min)) {
                if (!(e >= 0.0)) continue;
                // entity2 = entity;
                vec3d = raycastResult.orElse(min);
                entities.add(new EntityHit(entity, vec3d, 0));
                e = 0.0;
                continue;
            }
            if (!raycastResult.isPresent() || !((f = min.distanceToSqr(hitPosition = raycastResult.get())) < e) && e != 0.0) continue;
            if (entity.getRootVehicle() == sourceEntity.getRootVehicle()) {
                if (e != 0.0) continue;
                // entity2 = entity;
                vec3d = hitPosition;
                entities.add(new EntityHit(entity, vec3d, entity.distanceToSqr(sourceEntity)));
                continue;
            }
            // entity2 = entity;
            vec3d = hitPosition;
            entities.add(new EntityHit(entity, vec3d, entity.distanceToSqr(sourceEntity)));
            //e = f;
        }
        // if (entity2 == null) {
        //     return null;
        // }
        return entities;
    }

    public static List<Entity> targetsFromArea(Entity caster, float range, Spell.Target.Area area, @Nullable Predicate<Entity> predicate) {
        var origin = caster.getEyePosition();
        return targetsFromArea(caster.level(), caster, origin, caster.getLookAngle(), range, area, predicate);
    }

    public static List<Entity> targetsFromArea(Level world, @Nullable Entity centerEntity, Vec3 origin, Vec3 look, float range, Spell.Target.Area area, @Nullable Predicate<Entity> predicate) {
        var horizontal = range * area.horizontal_range_multiplier;
        var vertical = range * area.vertical_range_multiplier;
        var initialBox = centerEntity != null
                ? centerEntity.getBoundingBox()
                : new AABB(origin, origin);
        var box = initialBox.inflate(
                // Extending bounding box to add some intersection tolerance
                // Range check will filter out entities that are too far
                horizontal + 0.5F,
                vertical + 0.5F,
                horizontal + 0.5F);
        var squaredDistance = range * range;
        var angle = area.angle_degrees / 2F;
        return world.getEntities(centerEntity, box, (target) -> {
            var targetCenter = target.position().add(0, target.getBbHeight() / 2F, 0);
            var distanceVector = VectorHelper.distanceVector(origin, target.getBoundingBox());
            return !target.isSpectator()
                    && target.isPickable()
                    // Predicate check
                    && (predicate == null
                        || predicate.test(target))
                    // Distance check
                    && ((range > 1)
                        ? targetCenter.distanceToSqr(origin) <= squaredDistance
                        : distanceVector.length() <= range)
                    // Angle check
                    && ((angle <= 0)
                        || (VectorHelper.angleBetween(look, targetCenter.subtract(origin)) <= angle)
                        || (VectorHelper.angleBetween(look, distanceVector) <= angle)
                        )
                    // Obstacle check
                    && (range < 1
                        || raycastObstacleFree(world, centerEntity, origin, targetCenter)
                        || raycastObstacleFree(world, centerEntity, origin, origin.add(distanceVector))
                        )
                    ;
        });
    }

    public static boolean isInLineOfSight(Entity attacker, Entity target) {
        var origin = attacker.getEyePosition();
        var targetCenter = target.position().add(0, target.getBbHeight() / 2F, 0);
        var distanceVector = VectorHelper.distanceVector(origin, target.getBoundingBox());
        return raycastObstacleFree(attacker.level(), attacker, origin, targetCenter)
                || raycastObstacleFree(attacker.level(), attacker, origin, origin.add(distanceVector));
    }

    private static BlockHitResult raycastObstacle(Level world, Entity entity, Vec3 start, Vec3 end) {
        if (entity != null) {
            return world.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
        } else {
            return world.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        }
    }

    private static boolean raycastObstacleFree(Level world, Entity entity, Vec3 start, Vec3 end) {
        var hit = raycastObstacle(world, entity, start, end);
        return hit.getType() != HitResult.Type.BLOCK;
    }

    public static boolean isTargetedByPlayer(Entity entity, Player player) {
        if (entity != null && entity.level().isClientSide() && player instanceof SpellCaster.Client casterClient) {
            var targets = casterClient.getCurrentTargets();
            if (entity instanceof EnderDragon dragon) {
                // Targets contain any of the dragon's body parts
                for (var part : dragon.getSubEntities()) {
                    if (targets.contains(part)) {
                        return true;
                    }
                }
                return false;
            } else {
                return targets.contains(entity);
            }
        }
        return false;
    }

    public static Beam.Position castBeam(LivingEntity caster, Vec3 direction, float max) {
        var start = LaunchGeometry.launchPoint(caster);
        var end = start.add(direction.scale(max));
        var length = max;
        boolean hitBlock = false;
        var hit = caster.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        if (hit.getType() == HitResult.Type.BLOCK) {
            hitBlock = true;
            end = hit.getLocation();
            length = (float) start.distanceTo(hit.getLocation());
        }
        return new Beam.Position(start, end, length, hitBlock);
    }

    @Nullable public static Vec3 findSolidBelow(Entity entity, Vec3 position, Level world, float height) {
        var hit = world.clip(new ClipContext(position, position.add(0, height, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
        if (hit.getType() == HitResult.Type.BLOCK) {
            return hit.getLocation();
        }
        return null;
    }

    @Nullable public static Vec3 findSolidBlockBelow(@Nullable Entity entity, Vec3 position, Level world, float height) {
        var raycast = entity != null
                ? new ClipContext(position, position.add(0, height, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity)
                : new ClipContext(position, position.add(0, height, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty());
        var hit =  world.clip(raycast);
        if (hit.getType() == HitResult.Type.BLOCK) {
            var blockHit = (BlockHitResult)hit;
            return new Vec3(position.x(), blockHit.getBlockPos().getY() + 1F, position.z());
        }
        return null;
    }

    @Nullable public static Vec3 findTeleportDestination(LivingEntity entity, Vec3 look, float distance, int clearanceY) {
        var world = entity.level();
        var start = entity.getEyePosition();
        var end = start.add(look.scale(distance));
        var hit = world.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));

        Vec3 hitPosition = null;
        if (hit.getType() == HitResult.Type.MISS) {
            hitPosition = end;
        }
        if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos() != null) {
            hitPosition= hit.getLocation();
        }

        if (hitPosition != null) {
            var inverseLook = look.scale(-1);
            var paddedHitPosition = hitPosition.add(inverseLook.scale(0.5F));
            var hitDistance = start.distanceTo(paddedHitPosition);

            float reverted = 0;
            while (reverted < hitDistance) {
                var blockPos = new BlockPos((int)paddedHitPosition.x(), (int)paddedHitPosition.y(), (int)paddedHitPosition.z());
                if (isSafeWithClearance(world, blockPos, clearanceY)) {
                    return paddedHitPosition;
                }

                reverted += 1;
                paddedHitPosition = paddedHitPosition.add(inverseLook);
            }
        }
        return null;
    }

    private static boolean isSafeWithClearance(Level world, BlockPos blockPos, int clearanceY) {
        if (isSafeTeleportDestination(world, blockPos)) {
            var clearanceSafe = true;
            for (int i = 0; i < clearanceY; i++) {
                var clearancePos = blockPos.above(i);
                if (!isSafeTeleportDestination(world, clearancePos)) {
                    clearanceSafe = false;
                    break;
                }
            }
            return clearanceSafe;
        }
        return false;
    }

    private static boolean isSafeTeleportDestination(Level world, BlockPos pos) {
        var state = world.getBlockState(pos);
        return !(state.isSolid() || state.isSuffocating(world, pos));
    }
}
