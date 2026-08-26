package net.spell_engine.internals.delivery.melee;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.spell_engine.compat.MeleeCompat;
import net.spell_engine.utils.VectorHelper;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;

public class TargetFinder {
    public static class TargetResult {
        public Entity cursorTarget;
        public List<Entity> entities;
        public OrientedBoundingBox obb;
        public TargetResult(@Nullable Entity cursorTarget, List<Entity> entities, OrientedBoundingBox obb) {
            this.entities = entities;
            this.obb = obb;
        }
    }

    public static TargetResult findAttackTargetResult(Player player, @Nullable Entity cursorTarget, Vec3 hitboxSize, double arc, double attackRange, float roll) {
//        long startTime = System.nanoTime();
        Vec3 origin = getInitialTracingPoint(player);
        List<Entity> entities = getInitialTargets(player, cursorTarget, attackRange);

        boolean isSpinAttack = arc > 180;
        Vec3 size = hitboxSize;
        var obb = new OrientedBoundingBox(origin, size, player.getXRot(), player.getYRot(), roll);
        if (!isSpinAttack) {
            obb = obb.offsetAlongAxisZ(size.z / 2F);
        }
        obb.updateVertex();

        var collisionFilter = new CollisionFilter(obb);
        entities = collisionFilter.filter(entities);
        var radialFilter = new RadialFilter(origin, obb.axisZ, attackRange, arc);
        entities = radialFilter.filter(entities);
//        long elapsedTime = System.nanoTime() - startTime;
//        System.out.println("TargetResult findAttackTargetResult (ms): " + ((double)elapsedTime) / 1000000.0);
        return new TargetResult(cursorTarget, entities, obb);
    }
    
    public static Vec3 getInitialTracingPoint(Player player) {
        double shoulderHeight = player.getBbHeight() * 0.15 * player.getAgeScale();
        return player.getEyePosition().subtract(0, shoulderHeight, 0);
    }

    public static List<Entity> getInitialTargets(Player player, Entity cursorTarget, double attackRange) {
        AABB box = player.getBoundingBox().inflate(attackRange * 2F + 1.0);
        List<Entity> entities = player
                .level()
                .getEntities(player, box, entity ->  !entity.isSpectator() && entity.isPickable())
                .stream()
                .filter(entity -> entity != player
                        && entity.isAttackable()
                        && entity != cursorTarget
                        // && TargetHelper.isHitAllowed(false, TargetHelper.getRelation(player, entity)) // isDirect: false due to not being the cursor target
                        && (!entity.equals(player.getVehicle()) || isAttackableMount(entity)))
                .collect(Collectors.toList());
        if (cursorTarget != null && cursorTarget.isAttackable()) {
            entities.add(cursorTarget);
        }
        return entities;
    }

    public static boolean isAttackableMount(Entity entity) {
        return entity instanceof Monster || MeleeCompat.isEntityHostileVehicle.apply(entity);
    }

    public interface Filter {
        List<Entity> filter(List<Entity> entities);
    }

    public static class CollisionFilter implements Filter {
        private OrientedBoundingBox obb;

        public CollisionFilter(OrientedBoundingBox obb) {
            this.obb = obb;
        }

        @Override
        public List<Entity> filter(List<Entity> entities) {
            return entities.stream()
                    .filter(entity -> obb.intersects(entity.getBoundingBox().inflate(entity.getPickRadius()))
                                || obb.contains(entity.position().add(0, entity.getBbHeight() / 2F, 0))
                    )
                    .collect(Collectors.toList());
        }
    }

    public static class RadialFilter implements Filter {
        final private Vec3 origin;
        final private Vec3 orientation;
        final private double attackRange;
        final private double attackAngle;

        public RadialFilter(Vec3 origin, Vec3 orientation, double attackRange, double attackAngle) {
            this.origin = origin;
            this.orientation = orientation;
            this.attackRange = attackRange;
            this.attackAngle = Mth.clamp(attackAngle, 0, 360);
        }

        @Override
        public List<Entity> filter(List<Entity> entities) {
            return entities.stream()
                    .filter(entity -> {
                        var maxAngleDif = (attackAngle / 2.0);
                        Vec3 distanceVector = VectorHelper.distanceVector(origin, entity.getBoundingBox());
                        Vec3 positionVector = entity.position().add(0, entity.getBbHeight() / 2F, 0).subtract(origin);
                        return distanceVector.length() <= attackRange
                                && ((attackAngle == 0)
                                    || (VectorHelper.angleBetween(positionVector, orientation) <= maxAngleDif
                                    || VectorHelper.angleBetween(distanceVector, orientation) <= maxAngleDif))
                                && (rayContainsNoObstacle(origin, origin.add(distanceVector))
                                    || rayContainsNoObstacle(origin, origin.add(positionVector)));
                    })
                    .collect(Collectors.toList());
        }

        private static boolean rayContainsNoObstacle(Vec3 start, Vec3 end) {
            var client = Minecraft.getInstance();
            BlockHitResult hit = null;
            if (client.level != null) {
                hit = client.level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, client.player));
            }
            if (hit != null) {
                return hit.getType() != HitResult.Type.BLOCK;
            }
            return false;
        }
    }
}