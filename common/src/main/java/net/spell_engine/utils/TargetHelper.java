package net.spell_engine.utils;

import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.internals.delivery.Beam;
import net.spell_engine.internals.casting.SpellCasterClient;
import net.spell_engine.internals.SpellHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class TargetHelper {

    public static Vec3d locationFromRayCast(Entity caster, float range) {
        Vec3d start = caster.getEyePos();
        Vec3d look = caster.getRotationVec(1.0F)
                .normalize()
                .multiply(range);
        Vec3d end = start.add(look);
        var hit = raycastObstacle(caster.getWorld(), caster, start, end);
        if (hit.getType() == HitResult.Type.BLOCK) {
            return hit.getPos();
        }
        return end;
    }

    public static Entity targetFromRaycast(Entity caster, float range, Predicate<Entity> predicate) {
        Vec3d start = caster.getEyePos();
        Vec3d look = caster.getRotationVec(1.0F)
                .normalize()
                .multiply(range);
        Vec3d end = start.add(look);
        Box searchAABB = caster.getBoundingBox().expand(range, range, range);
        var hitResult = ProjectileUtil.raycast(caster, start, end, searchAABB, (target) -> {
            return !target.isSpectator() && target.canHit() && predicate.test(target);
        }, range*range); // `range*range` is provided for squared distance comparison
        if (hitResult != null) {
            if (hitResult.getPos() == null || raycastObstacleFree(caster.getWorld(), caster, start, hitResult.getPos())) {
                return hitResult.getEntity();
            }
        }
        return null;
    }

    public static List<Entity> targetsFromRaycast(Entity caster, float range, Predicate<Entity> predicate) {
        Vec3d start = caster.getEyePos();
        Vec3d look = caster.getRotationVec(1.0F)
                .normalize()
                .multiply(range);
        Vec3d end = start.add(look);
        Box searchAABB = caster.getBoundingBox().expand(range, range, range);
        var entitiesHit = TargetHelper.raycastMultiple(caster, start, end, searchAABB, (target) -> {
            return !target.isSpectator() && target.canHit() && predicate.test(target);
        }, range*range); // `range*range` is provided for squared distance comparison
        return entitiesHit.stream()
                .filter((hit) -> hit.position() == null || raycastObstacleFree(caster.getWorld(), caster, start, hit.position()))
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

    private record EntityHit(Entity entity, Vec3d position, double squaredDistanceToSource) { }

    @Nullable
    private static List<EntityHit> raycastMultiple(Entity sourceEntity, Vec3d min, Vec3d max, Box searchBox, Predicate<Entity> predicate, double squaredDistance) {
        World world = sourceEntity.getWorld();
        double e = squaredDistance;
        // Entity entity2 = null;
        List<EntityHit> entities = new ArrayList<>();
        Vec3d vec3d = null;
        for (Entity entity : world.getOtherEntities(sourceEntity, searchBox, predicate)) {
            Vec3d hitPosition;
            double f;
            Box box2 = entity.getBoundingBox().expand(entity.getTargetingMargin());
            Optional<Vec3d> raycastResult = box2.raycast(min, max);
            if (box2.contains(min)) {
                if (!(e >= 0.0)) continue;
                // entity2 = entity;
                vec3d = raycastResult.orElse(min);
                entities.add(new EntityHit(entity, vec3d, 0));
                e = 0.0;
                continue;
            }
            if (!raycastResult.isPresent() || !((f = min.squaredDistanceTo(hitPosition = raycastResult.get())) < e) && e != 0.0) continue;
            if (entity.getRootVehicle() == sourceEntity.getRootVehicle()) {
                if (e != 0.0) continue;
                // entity2 = entity;
                vec3d = hitPosition;
                entities.add(new EntityHit(entity, vec3d, entity.squaredDistanceTo(sourceEntity)));
                continue;
            }
            // entity2 = entity;
            vec3d = hitPosition;
            entities.add(new EntityHit(entity, vec3d, entity.squaredDistanceTo(sourceEntity)));
            //e = f;
        }
        // if (entity2 == null) {
        //     return null;
        // }
        return entities;
    }

    public static List<Entity> targetsFromArea(Entity caster, float range, Spell.Target.Area area, @Nullable Predicate<Entity> predicate) {
        var origin = caster.getEyePos();
        return targetsFromArea(caster.getWorld(), caster, origin, caster.getRotationVector(), range, area, predicate);
    }

    public static List<Entity> targetsFromArea(World world, @Nullable Entity centerEntity, Vec3d origin, Vec3d look, float range, Spell.Target.Area area, @Nullable Predicate<Entity> predicate) {
        var horizontal = range * area.horizontal_range_multiplier;
        var vertical = range * area.vertical_range_multiplier;
        var initialBox = centerEntity != null
                ? centerEntity.getBoundingBox()
                : new Box(origin, origin);
        var box = initialBox.expand(
                // Extending bounding box to add some intersection tolerance
                // Range check will filter out entities that are too far
                horizontal + 0.5F,
                vertical + 0.5F,
                horizontal + 0.5F);
        var squaredDistance = range * range;
        var angle = area.angle_degrees / 2F;
        return world.getOtherEntities(centerEntity, box, (target) -> {
            var targetCenter = target.getPos().add(0, target.getHeight() / 2F, 0);
            var distanceVector = VectorHelper.distanceVector(origin, target.getBoundingBox());
            return !target.isSpectator()
                    && target.canHit()
                    // Predicate check
                    && (predicate == null
                        || predicate.test(target))
                    // Distance check
                    && ((range > 1)
                        ? targetCenter.squaredDistanceTo(origin) <= squaredDistance
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
        var origin = attacker.getEyePos();
        var targetCenter = target.getPos().add(0, target.getHeight() / 2F, 0);
        var distanceVector = VectorHelper.distanceVector(origin, target.getBoundingBox());
        return raycastObstacleFree(attacker.getWorld(), attacker, origin, targetCenter)
                || raycastObstacleFree(attacker.getWorld(), attacker, origin, origin.add(distanceVector));
    }

    private static BlockHitResult raycastObstacle(World world, Entity entity, Vec3d start, Vec3d end) {
        if (entity != null) {
            return world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, entity));
        } else {
            return world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, ShapeContext.absent()));
        }
    }

    private static boolean raycastObstacleFree(World world, Entity entity, Vec3d start, Vec3d end) {
        var hit = raycastObstacle(world, entity, start, end);
        return hit.getType() != HitResult.Type.BLOCK;
    }

    public static boolean isTargetedByPlayer(Entity entity, PlayerEntity player) {
        if (entity != null && entity.getWorld().isClient && player instanceof SpellCasterClient casterClient) {
            var targets = casterClient.getCurrentTargets();
            if (entity instanceof EnderDragonEntity dragon) {
                // Targets contain any of the dragon's body parts
                for (var part : dragon.getBodyParts()) {
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

    public static Beam.Position castBeam(LivingEntity caster, Vec3d direction, float max) {
        var start = SpellHelper.launchPoint(caster);
        var end = start.add(direction.multiply(max));
        var length = max;
        boolean hitBlock = false;
        var hit = caster.getWorld().raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, caster));
        if (hit.getType() == HitResult.Type.BLOCK) {
            hitBlock = true;
            end = hit.getPos();
            length = (float) start.distanceTo(hit.getPos());
        }
        return new Beam.Position(start, end, length, hitBlock);
    }

    @Nullable public static Vec3d findSolidBelow(Entity entity, Vec3d position, World world, float height) {
        var hit = world.raycast(new RaycastContext(position, position.add(0, height, 0),
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, entity));
        if (hit.getType() == HitResult.Type.BLOCK) {
            return hit.getPos();
        }
        return null;
    }

    @Nullable public static Vec3d findSolidBlockBelow(@Nullable Entity entity, Vec3d position, World world, float height) {
        var raycast = entity != null
                ? new RaycastContext(position, position.add(0, height, 0), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, entity)
                : new RaycastContext(position, position.add(0, height, 0), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, ShapeContext.absent());
        var hit =  world.raycast(raycast);
        if (hit.getType() == HitResult.Type.BLOCK) {
            var blockHit = (BlockHitResult)hit;
            return new Vec3d(position.getX(), blockHit.getBlockPos().getY() + 1F, position.getZ());
        }
        return null;
    }

    @Nullable public static Vec3d findTeleportDestination(LivingEntity entity, Vec3d look, float distance, int clearanceY) {
        var world = entity.getWorld();
        var start = entity.getEyePos();
        var end = start.add(look.multiply(distance));
        var hit = world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, entity));

        Vec3d hitPosition = null;
        if (hit.getType() == HitResult.Type.MISS) {
            hitPosition = end;
        }
        if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos() != null) {
            hitPosition= hit.getPos();
        }

        if (hitPosition != null) {
            var inverseLook = look.multiply(-1);
            var paddedHitPosition = hitPosition.add(inverseLook.multiply(0.5F));
            var hitDistance = start.distanceTo(paddedHitPosition);

            float reverted = 0;
            while (reverted < hitDistance) {
                var blockPos = new BlockPos((int)paddedHitPosition.getX(), (int)paddedHitPosition.getY(), (int)paddedHitPosition.getZ());
                if (isSafeWithClearance(world, blockPos, clearanceY)) {
                    return paddedHitPosition;
                }

                reverted += 1;
                paddedHitPosition = paddedHitPosition.add(inverseLook);
            }
        }
        return null;
    }

    private static boolean isSafeWithClearance(World world, BlockPos blockPos, int clearanceY) {
        if (isSafeTeleportDestination(world, blockPos)) {
            var clearanceSafe = true;
            for (int i = 0; i < clearanceY; i++) {
                var clearancePos = blockPos.up(i);
                if (!isSafeTeleportDestination(world, clearancePos)) {
                    clearanceSafe = false;
                    break;
                }
            }
            return clearanceSafe;
        }
        return false;
    }

    private static boolean isSafeTeleportDestination(World world, BlockPos pos) {
        var state = world.getBlockState(pos);
        return !(state.isSolid() || state.shouldSuffocate(world, pos));
    }
}
