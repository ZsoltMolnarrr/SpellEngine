package net.spell_engine.utils;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.decoration.AbstractDecorationEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.internals.Beam;
import net.spell_engine.internals.SpellCasterClient;
import net.spell_engine.internals.SpellHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class TargetHelper {
    public enum Intent {
        HELPFUL, HARMFUL
    }
    public enum TargetingMode {
        DIRECT, AREA
    }
    public enum Relation {
        FRIENDLY, NEUTRAL, HOSTILE;

        public static Relation coalesce(Relation value, Relation fallback) {
            if (value != null) {
                return value;
            }
            return fallback;
        }
    }

    public static Relation getRelation(LivingEntity attacker, Entity target) {
        if (attacker == target) {
            return Relation.FRIENDLY;
        }
        var casterTeam = attacker.getScoreboardTeam();
        var targetTeam = target.getScoreboardTeam();
        if (target instanceof Tameable tameable) {
            var owner = tameable.getOwner();
            if (owner != null) {
                return getRelation(attacker, owner);
            }
        }
        if (target instanceof AbstractDecorationEntity) {
            return Relation.NEUTRAL;
        }
        var config = SpellEngineMod.config;
        if (casterTeam == null || targetTeam == null) {
            if (target instanceof PlayerEntity) {
                return Relation.coalesce(config.player_relation_to_teamless_players, Relation.NEUTRAL);
            }
            if (target instanceof VillagerEntity) {
                return Relation.coalesce(config.player_relation_to_villagers, Relation.NEUTRAL);
            }
            if (target instanceof PassiveEntity) {
                return Relation.coalesce(config.player_relation_to_passives, Relation.HOSTILE);
            }
            if (target instanceof HostileEntity) {
                return Relation.coalesce(config.player_relation_to_hostiles, Relation.HOSTILE);
            }
            return Relation.coalesce(config.player_relation_to_other, Relation.HOSTILE);
        } else {
            return attacker.isTeammate(target) ? Relation.FRIENDLY : Relation.HOSTILE;
        }
    }

    // Make sure this complies with comment in `ServerConfig`
    private static boolean[][] TABLE_OF_ULTIMATE_JUSTICE = {
            // Friendly, Neutral, Hostile
            { false, true, true, }, // Direct Damage
            { false, false, true }, // Area Damage
            { true, true, false },  // Direct Healing
            { true, false, false }, // Area Healing
    };

    public static boolean actionAllowed(TargetingMode targetingMode, Intent intent, LivingEntity attacker, Entity target) {
        var relation = getRelation(attacker, target);
        int row = 0;
        int column = 0;
        if (intent == Intent.HELPFUL) {
            row += 2;
        }
        if (targetingMode == TargetingMode.AREA) {
            row += 1;
        }
        switch (relation) {
            case FRIENDLY -> {
                column = 0;
            }
            case NEUTRAL -> {
                column = 1;
            }
            case HOSTILE -> {
                column = 2;
            }
        }
        return TABLE_OF_ULTIMATE_JUSTICE[row][column];
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
            if (hitResult.getPos() == null || raycastObstacleFree(caster, start, hitResult.getPos())) {
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
                .filter((hit) -> hit.position() == null || raycastObstacleFree(caster, start, hit.position()))
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

    public static List<Entity> targetsFromArea(Entity caster, float range, Spell.Release.Target.Area area, @Nullable Predicate<Entity> predicate) {
        var origin = caster.getEyePos();
        return targetsFromArea(caster, origin, range, area, predicate);
    }

    public static List<Entity> targetsFromArea(Entity centerEntity, Vec3d origin, float range, Spell.Release.Target.Area area, @Nullable Predicate<Entity> predicate) {
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
                    && (predicate == null || predicate.test(target))
                    && target.squaredDistanceTo(centerEntity) <= squaredDistance
                    && ((angle <= 0)
                        || (VectorHelper.angleBetween(look, targetCenter.subtract(origin)) <= angle)
                        || (VectorHelper.angleBetween(look, distanceVector) <= angle)
                        )
                    && (raycastObstacleFree(centerEntity, origin, targetCenter)
                        || raycastObstacleFree(centerEntity, origin, origin.add(distanceVector) )
                        );
        });
        return entities;
    }

    public static boolean isInLineOfSight(Entity attacker, Entity target) {
        var origin = attacker.getEyePos();
        var targetCenter = target.getPos().add(0, target.getHeight() / 2F, 0);
        var distanceVector = VectorHelper.distanceVector(origin, target.getBoundingBox());
        return raycastObstacleFree(attacker, origin, targetCenter)
                || raycastObstacleFree(attacker, origin, origin.add(distanceVector));
    }

    private static boolean raycastObstacleFree(Entity entity, Vec3d start, Vec3d end) {
        var hit = entity.world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, entity));
        return hit.getType() != HitResult.Type.BLOCK;
    }

    public static boolean isTargetedByPlayer(Entity entity, PlayerEntity player) {
        if (entity.world.isClient && player instanceof SpellCasterClient casterClient) {
            return casterClient.getCurrentTargets().contains(entity);
        }
        return false;
    }

    public static Beam.Position castBeam(LivingEntity caster, Vec3d direction, float max) {
        var start = SpellHelper.launchPoint(caster);
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
