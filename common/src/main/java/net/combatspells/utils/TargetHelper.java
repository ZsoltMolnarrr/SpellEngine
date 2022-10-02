package net.combatspells.utils;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

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
        if (casterTeam == null && targetTeam == null) {
            if (caster instanceof PlayerEntity casterPlayer) {
                if (target instanceof PlayerEntity targetEntity) {
                    return Relation.FRIENDLY;
                }
            }
            return Relation.NEUTRAL;
        }
        return caster.isTeammate(target) ? Relation.FRIENDLY : Relation.HOSTILE;
    }

    public static boolean actionAllowed(boolean beneficial, Relation relation) {
        switch (relation) {
            case FRIENDLY -> {
                return beneficial;
            }
            case NEUTRAL, HOSTILE -> {
                return !beneficial;
            }
        }
        assert true;
        return true;
    }

    public static Entity raycastForTarget(Entity caster, double range) {
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
}
