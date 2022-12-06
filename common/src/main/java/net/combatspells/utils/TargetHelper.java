package net.combatspells.utils;

import net.combatspells.api.spell.Spell;
import net.combatspells.internals.Beam;
import net.combatspells.internals.SpellCasterClient;
import net.combatspells.internals.SpellHelper;
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
            return hitResult.getEntity();
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
        return TargetHelper.raycastMultiple(caster, start, end, searchAABB, (target) -> {
            return !target.isSpectator() && target.canHit();
        }, range*range); // `range*range` is provided for squared distance comparison
    }

    @Nullable
    private static List<Entity> raycastMultiple(Entity entity, Vec3d min, Vec3d max, Box box, Predicate<Entity> predicate, double d) {
        World world = entity.world;
        double e = d;
        // Entity entity2 = null;
        List<Entity> entities = new ArrayList<>();
        Vec3d vec3d = null;
        for (Entity entity3 : world.getOtherEntities(entity, box, predicate)) {
            Vec3d vec3d2;
            double f;
            Box box2 = entity3.getBoundingBox().expand(entity3.getTargetingMargin());
            Optional<Vec3d> optional = box2.raycast(min, max);
            if (box2.contains(min)) {
                if (!(e >= 0.0)) continue;
                // entity2 = entity3;
                entities.add(entity3);
                vec3d = optional.orElse(min);
                e = 0.0;
                continue;
            }
            if (!optional.isPresent() || !((f = min.squaredDistanceTo(vec3d2 = optional.get())) < e) && e != 0.0) continue;
            if (entity3.getRootVehicle() == entity.getRootVehicle()) {
                if (e != 0.0) continue;
                // entity2 = entity3;
                entities.add(entity3);
                vec3d = vec3d2;
                continue;
            }
            // entity2 = entity3;
            entities.add(entity3);
            vec3d = vec3d2;
            //e = f;
        }
        // if (entity2 == null) {
        //     return null;
        // }
        return entities;
    }

    public static List<Entity> targetsFromArea(Entity caster, float range, Spell.Release.Target.Area area) {
        var horizontal = range * area.horizontal_range_multiplier;
        var vertical = range * area.vertical_range_multiplier;
        var box = caster.getBoundingBox().expand(
                horizontal,
                vertical,
                horizontal);
        var squaredDistance = range * range;
        var raycastStart = caster.getEyePos();
        var entities = caster.world.getOtherEntities(caster, box, (target) -> {
            return !target.isSpectator() && target.canHit()
                    && target.squaredDistanceTo(caster) <= squaredDistance
                    && raycastObstacleFree(raycastStart, target.getPos().add(0, target.getHeight() / 2F, 0));
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
