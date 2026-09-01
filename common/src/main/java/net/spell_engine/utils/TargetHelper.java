package net.spell_engine.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
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
        return castBeam(caster, LaunchGeometry.launchPoint(caster), direction, max);
    }

    /// Same, with an explicit origin — so a renderer can cast the beam from the very point it draws it
    /// from (the caster's interpolated render-frame launch point) instead of the tick-time one.
    public static Beam.Position castBeam(LivingEntity caster, Vec3 start, Vec3 direction, float max) {
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

    /// The exact point where a downward ray from `position` meets the ground, or `null` when nothing
    /// is hit within `height` (negative = downward). The Y is the top of the actual collision shape,
    /// so partial blocks (carpet, snow layer, slab, path) resolve to their real surface instead of a
    /// full block top. Decoration is not ground — see {@link GroundRaycastContext}.
    /// How far above the queried position the downward ground ray starts. A ray beginning exactly on a
    /// block boundary cannot see a shape whose bottom *is* that boundary — `Box.raycast` requires a
    /// strictly positive travel distance — so a thin surface at the query's own level (a carpet or snow
    /// layer on the floor beside the caster) would be missed and the placement would resolve to the
    /// floor *underneath* it. Starting a block higher makes those visible without reaching into the
    /// level above: a full block there is entered at that same rejected zero distance, so it stays unhit.
    private static final double GROUND_SEARCH_PRE_LIFT = 1.0;

    @Nullable public static Vec3 findSolidBelow(@Nullable Entity entity, Vec3 position, Level world, float height) {
        var shapeContext = entity != null ? CollisionContext.of(entity) : CollisionContext.empty();
        // The pre-lift and the surface-top logic below are downward-search semantics; an upward search
        // (positive height, e.g. a positive `aim.reposition_vertically`) keeps the plain ray — lifting
        // its start would flip a short upward ray into a downward one.
        if (height >= 0) {
            var upHit = world.clip(new GroundRaycastContext(position, position.add(0, height, 0), shapeContext));
            return upHit.getType() == HitResult.Type.BLOCK ? upHit.getLocation() : null;
        }
        var start = position.add(0, GROUND_SEARCH_PRE_LIFT, 0);
        var hit = world.clip(new GroundRaycastContext(start, position.add(0, height, 0), shapeContext));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        if (hit.isInside()) {
            // The ray began inside the shape it hit — the common case, since a placement anchored at
            // the caster's feet starts exactly on the surface and `VoxelShape.raycast` advances the
            // start by 0.1% of the ray before testing. Such a hit reports that advanced point, which
            // is *below* the surface; take the shape's top instead. Getting this wrong buries the
            // placement by a hair, which `EntityPlacements` then reads as embedded terrain and lifts
            // by a whole block.
            var blockPos = hit.getBlockPos();
            var shape = world.getBlockState(blockPos).getCollisionShape(world, blockPos);
            var top = shape.isEmpty() ? 1.0 : shape.max(Direction.Axis.Y);
            return new Vec3(hit.getLocation().x(), blockPos.getY() + top, hit.getLocation().z());
        }
        return hit.getLocation();
    }

    /// Same search as {@link #findSolidBelow}, but kept in the query's own column: the hit's X/Z are
    /// replaced by `position`'s, so a placement lands straight below where it was asked for even when
    /// the ray grazes a shape's side face.
    @Nullable public static Vec3 findSolidBlockBelow(@Nullable Entity entity, Vec3 position, Level world, float height) {
        var ground = findSolidBelow(entity, position, world, height);
        if (ground == null) {
            return null;
        }
        return new Vec3(position.x(), ground.y(), position.z());
    }

    /// A `COLLIDER` raycast that treats zero-hardness, non-solid blocks — grass, flowers, fire, lily
    /// pads, berry bushes — as air, so a placement lands on the ground *under* the decoration rather
    /// than on top of it. Zero-hardness blocks that are genuinely standable (TNT, slime block: full
    /// cube collision, hence solid) stay ground.
    ///
    /// Filtering by shape rather than by re-casting from under each rejected block: `BlockView.raycast`
    /// asks the context for every visited block's shape, and an empty shape makes the traversal walk
    /// straight through — one pass, and a rejected block never reaches voxel math (both checks below
    /// read cached block state fields).
    private static class GroundRaycastContext extends ClipContext {
        GroundRaycastContext(Vec3 start, Vec3 end, CollisionContext shapeContext) {
            super(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shapeContext);
        }

        @Override
        public VoxelShape getBlockShape(BlockState state, BlockGetter world, BlockPos pos) {
            if (state.getDestroySpeed(world, pos) == 0F && !state.isSolid()) {
                return Shapes.empty();
            }
            return super.getBlockShape(state, world, pos);
        }
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
