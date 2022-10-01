package net.combatspells.utils;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.player.PlayerEntity;

public class TargetHelper {
    public enum Relation {
        FRIENDLY, NEUTRAL, HOSTILE
    }
    public static Relation getRelation(LivingEntity caster, Entity target) {
        var casterTeam = caster.getScoreboardTeam();
        var targetTeam = target.getScoreboardTeam();
        if (target instanceof Tameable tameable) {
            if (tameable.getOwner().equals(caster)) {
                return Relation.FRIENDLY;
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
}
