package net.spell_engine.internals.target;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.decoration.AbstractDecorationEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.registry.Registries;
import net.minecraft.scoreboard.AbstractTeam;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.compat.MultipartEntityCompat;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

public class EntityRelations {
    public static EntityRelation getRelation(LivingEntity attacker, Entity target) {
        if (attacker == target) {
            return EntityRelation.ALLY;
        }
        target = MultipartEntityCompat.coalesce(target);

        if (target instanceof Tameable tameable) {
            var owner = tameable.getOwner();
            if (owner != null) {
                return getRelation(attacker, owner);
            }
        }
        if (target instanceof AbstractDecorationEntity) {
            return EntityRelation.NEUTRAL;
        }
        var config = SpellEngineMod.config;

        for (var matcher: TEAM_MATCHERS.values()) {
            var relation = matcher.getRelation(attacker, target);
            if (relation != null) {
                return relation.areTeammates()
                        ? (relation.friendlyFireAllowed() ? EntityRelation.FRIENDLY : EntityRelation.ALLY)
                        : EntityRelation.HOSTILE;
            }
        }

        var id = Registries.ENTITY_TYPE.getId(target.getType());
        var mappedRelation = config.player_relations.get(id.toString());
        if (mappedRelation != null) {
            return mappedRelation;
        }
        if (target instanceof PassiveEntity) {
            return EntityRelation.coalesce(config.player_relation_to_passives, EntityRelation.HOSTILE);
        }
        if (target instanceof HostileEntity) {
            return EntityRelation.coalesce(config.player_relation_to_hostiles, EntityRelation.HOSTILE);
        }
        return EntityRelation.coalesce(config.player_relation_to_other, EntityRelation.HOSTILE);
    }

    public record TeamRelation(boolean areTeammates, boolean friendlyFireAllowed) { }
    public interface TeamMatcher { @Nullable TeamRelation getRelation(Entity attacker, Entity target); }
    private static final Map<String, TeamMatcher> TEAM_MATCHERS = new LinkedHashMap<>();
    public static void registerTeamMatcher(String name, TeamMatcher matcher) {
        TEAM_MATCHERS.put(name, matcher);
    }
    static {
        registerTeamMatcher("vanilla", (entity1, entity2) -> {
            var team1 = entity1.getScoreboardTeam();
            var team2 = entity2.getScoreboardTeam();
            if (team1 == null || team2 == null) {
                return null;
            }
            var friendlyFire = team1.isFriendlyFireAllowed();
            return new TeamRelation(entity1.isTeammate(entity2), friendlyFire);
        });
    }

    // Make sure this complies with comment in `ServerConfig`
    private static final boolean[][] TABLE_OF_ULTIMATE_JUSTICE = {
            // ALLY     FRIENDLY        NEUTRAL HOSTILE MIXED
            { false,    true,           true,   true,   true }, // Direct Damage
            { false,    false,          false,  true,   true }, // Area Damage
            { true,     true,           true,   false,  true }, // Direct Healing
            { true,     true,           false,  false,  true }, // Area Healing
    };

    public static boolean actionAllowed(SpellTarget.FocusMode focusMode, SpellTarget.Intent intent, LivingEntity attacker, Entity target) {
        var relation = getRelation(attacker, target);

        int row = 0;
        if (intent == SpellTarget.Intent.HELPFUL) {
            row += 2;
        }
        if (focusMode == SpellTarget.FocusMode.AREA) {
            row += 1;
        }

        int column = 0;
        switch (relation) {
            case ALLY -> {
                column = 0;
            }
            case FRIENDLY -> {
                column = 1;
            }
            case NEUTRAL -> {
                column = 2;
            }
            case HOSTILE -> {
                column = 3;
            }
            case MIXED -> {
                column = 4;
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
}
