package net.spell_engine.internals.target;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.TeamManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.decoration.AbstractDecorationEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.scoreboard.AbstractTeam;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.compat.MultipartEntityCompat;

public class EntityRelations {
    public static EntityRelation getRelation(LivingEntity attacker, Entity target) {
        if (attacker == target) {
            return EntityRelation.ALLY;
        }
        target = MultipartEntityCompat.coalesce(target);

        var casterTeam = attacker.getScoreboardTeam();
        var targetTeam = target.getScoreboardTeam();
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
        if (casterTeam == null || targetTeam == null) {
            // --- FTB TEAMS ---
            if (FabricLoader.getInstance().isModLoaded("ftbteams") && attacker instanceof PlayerEntity attackerPlayer && target instanceof PlayerEntity targetPlayer) {
                boolean managerAvailable = attacker.getWorld().isClient ?
                        FTBTeamsAPI.api().isClientManagerLoaded() :
                        FTBTeamsAPI.api().isManagerLoaded();
                if (managerAvailable) {
                    TeamManager manager = FTBTeamsAPI.api().getManager();
                    if (manager.arePlayersInSameTeam(attackerPlayer.getUuid(), targetPlayer.getUuid())) {
                        return EntityRelation.ALLY;
                    }
                }
            }
            // --- END FTB TEAMS ---

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
        } else {
            return attacker.isTeammate(target)
                    ? (casterTeam.isFriendlyFireAllowed() ? EntityRelation.FRIENDLY : EntityRelation.ALLY)
                    : EntityRelation.HOSTILE;
        }
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
