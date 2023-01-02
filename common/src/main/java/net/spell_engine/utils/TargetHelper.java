package net.spell_engine.utils;

import net.spell_engine.api.spell.Spell;
import net.spell_engine.internals.Beam;
import net.spell_engine.internals.SpellCasterClient;
import net.spell_engine.internals.SpellHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class TargetHelper {
    public enum Relation {
        FRIENDLY, NEUTRAL, HOSTILE
    }

    public static Relation getRelation(LivingEntity caster, Entity target) {
        var casterTeam = caster.getScoreboardTeam();
        var targetTeam = target.getScoreboardTeam();
        if (target instanceof Tameable tameable) {
            var owner = tameable.getOwner();
            if (owner != null) {
                return getRelation(caster, owner);
            }
        }
        if (casterTeam == null || targetTeam == null) {
            if (caster instanceof PlayerEntity casterPlayer) {
                if (target instanceof PlayerEntity targetEntity) {
                    return Relation.FRIENDLY;
                }
            }
            return Relation.NEUTRAL;
        }
        return caster.isTeammate(target) ? Relation.FRIENDLY : Relation.HOSTILE;
    }

    public static boolean actionAllowed(boolean helpful, Relation relation, LivingEntity caster, Entity target) {
        switch (relation) {
            case FRIENDLY -> {
                if (helpful) {
                    return true;
                } else {
                    return allowedToHurt(caster, target);
                }
            }
            case NEUTRAL, HOSTILE -> {
                return !helpful; // Only allowed in case harmful
            }
        }
        assert true;
        return true;
    }

    // Generalized copy of shouldDamagePlayer
    public static boolean allowedToHurt(Entity e1, Entity e2) {
        AbstractTeam abstractTeam = e1.getScoreboardTeam();
        AbstractTeam abstractTeam2 = e2.getScoreboardTeam();
        if (abstractTeam == null) {
            return true;
        } else {
            return !abstractTeam.isEqual(abstractTeam2) || abstractTeam.isFriendlyFireAllowed();
        }
    }

    public static Entity targetFromRaycast(Entity caster, float range) {
        Vec3d start = caster.getEyePos();
        Vec3d look = caster.getRotationVec(1.0F)
                .normalize()
                .multiply(range);
        Vec3d end = start.add(look);
        Box searchAABB = caster.getBoundingBox().expand(range, range, range);
        var hitResult = ProjectileUtil.raycast(caster, start, end, searchAABB, (target) -> {
            return !target.isSpectator() && target.canHit();
        }, range*range); // `range*range` is provided for squared distance comparison
        if (hitResult != null) {
            if (hitResult.getPos() == null || raycastObstacleFree(start, hitResult.getPos())) {
                return hitResult.getEntity();
            }
        }
        return null;
    }

    public static List<Entity> targetsFromRaycast(Entity caster, float range) {
        Vec3d start = caster.getEyePos();
        Vec3d look = caster.getRotationVec(1.0F)
                .normalize()
                .multiply(range);
        Vec3d end = start.add(look);
        Box searchAABB = caster.getBoundingBox().expand(range, range, range);
        var entitiesHit = TargetHelper.raycastMultiple(caster, start, end, searchAABB, (target) -> {
            return !target.isSpectator() && target.canHit();
        }, range*range); // `range*range` is provided for squared distance comparison
        return entitiesHit.stream()
                .filter((hit) -> hit.position() == null || raycastObstacleFree(start, hit.position()))
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
        World world = sourceEntity.world;
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

    public static List<Entity> targetsFromArea(Entity caster, float range, Spell.Release.Target.Area area) {
        var origin = caster.getEyePos();
        return targetsFromArea(caster, origin, range, area);
    }

    public static List<Entity> targetsFromArea(Entity centerEntity, Vec3d origin, float range, Spell.Release.Target.Area area) {
        var horizontal = range * area.horizontal_range_multiplier;
        var vertical = range * area.vertical_range_multiplier;
        var box = centerEntity.getBoundingBox().expand(
                horizontal,
                vertical,
                horizontal);
        var squaredDistance = range * range;
        var look = centerEntity.getRotationVector();
        var angle = area.angle_degrees / 2F;
        var entities = centerEntity.world.getOtherEntities(centerEntity, box, (target) -> {
            var targetCenter = target.getPos().add(0, target.getHeight() / 2F, 0);
            var distanceVector = VectorHelper.distanceVector(origin, target.getBoundingBox());
            return !target.isSpectator() && target.canHit()
                    && target.squaredDistanceTo(centerEntity) <= squaredDistance
                    && ((angle <= 0)
                        || (VectorHelper.angleBetween(look, targetCenter.subtract(origin)) <= angle)
                        || (VectorHelper.angleBetween(look, distanceVector) <= angle)
                        )
                    && (raycastObstacleFree(origin, targetCenter)
                        || raycastObstacleFree(origin, origin.add(distanceVector) )
                        );
        });
        return entities;
    }

    private static boolean raycastObstacleFree(Vec3d start, Vec3d end) {
        var client = MinecraftClient.getInstance();
        var world = client.world;
        var hit = client.world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, client.player));
        return hit.getType() != HitResult.Type.BLOCK;
    }

    public static boolean isTargetedByClientPlayer(Entity entity) {
        if (entity.world.isClient) {
            var clientPlayer = MinecraftClient.getInstance().player;
            return ((SpellCasterClient) clientPlayer).getCurrentTargets().contains(entity);
        }
        return false;
    }

    public static Beam.Position castBeam(LivingEntity caster, Vec3d direction, float max) {
        var start = SpellHelper.launchPoint(caster, 0.15F);
        var end = start.add(direction.multiply(max));
        var length = max;
        boolean hitBlock = false;
        var hit = caster.world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, caster));
        if (hit.getType() == HitResult.Type.BLOCK) {
            hitBlock = true;
            end = hit.getPos();
            length = (float) start.distanceTo(hit.getPos());
        }
        return new Beam.Position(start, end, length, hitBlock);
    }
}
