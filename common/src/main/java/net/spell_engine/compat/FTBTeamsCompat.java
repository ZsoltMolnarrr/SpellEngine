package net.spell_engine.compat;
import net.minecraft.world.entity.player.Player;
import net.spell_engine.Platform;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.client.KnownClientPlayer;
import net.spell_engine.internals.target.EntityRelations;

import java.util.Optional;

public class FTBTeamsCompat {
    public static void init() {
        if (Platform.util().isModLoaded("ftbteams")) {
            EntityRelations.registerTeamMatcher("ftb", (attack, target) -> {
                if (attack instanceof Player attackerPlayer && target instanceof Player targetPlayer) {
                    if (attackerPlayer.level().isClientSide()) {
                        return checkClientTeamRelation(attackerPlayer, targetPlayer);
                    } else {
                        return checkServerTeamRelation(attackerPlayer, targetPlayer);
                    }
                }
                return null;
            });
        }
    }

    private static EntityRelations.TeamRelation checkClientTeamRelation(Player attackerPlayer, Player targetPlayer) {
        if (!FTBTeamsAPI.api().isClientManagerLoaded()) {
            return null;
        }
        var manager = FTBTeamsAPI.api().getClientManager();

        Optional<KnownClientPlayer> attackerKnownPlayerOpt = manager.getKnownPlayer(attackerPlayer.getUUID());
        if (attackerKnownPlayerOpt.isEmpty()) {
            return null;
        }

        Optional<KnownClientPlayer> targetKnownPlayerOpt = manager.getKnownPlayer(targetPlayer.getUUID());
        if (targetKnownPlayerOpt.isEmpty()) {
            return null;
        }

        KnownClientPlayer attackerKnownPlayer = attackerKnownPlayerOpt.get();
        KnownClientPlayer targetKnownPlayer = targetKnownPlayerOpt.get();

        if (attackerKnownPlayer.teamId().equals(targetKnownPlayer.teamId())) {
            return new EntityRelations.TeamRelation(true, false);
        }

        // --- ANTI-ABUSE: Check for MUTUAL alliance ---
        Optional<Team> attackerTeamOpt = manager.getTeamByID(attackerKnownPlayer.teamId());
        Optional<Team> targetTeamOpt = manager.getTeamByID(targetKnownPlayer.teamId());

        if (attackerTeamOpt.isPresent() && targetTeamOpt.isPresent()) {
            // Check if Attacker's team considers Target an ally
            boolean attackerSeesAlly = attackerTeamOpt.get().getRankForPlayer(targetPlayer.getUUID()).isAllyOrBetter();
            // Check if Target's team considers Attacker an ally
            boolean targetSeesAlly = targetTeamOpt.get().getRankForPlayer(attackerPlayer.getUUID()).isAllyOrBetter();

            if (attackerSeesAlly && targetSeesAlly) {
                return new EntityRelations.TeamRelation(true, false);
            }
        }

        return null;
    }

    private static EntityRelations.TeamRelation checkServerTeamRelation(Player attackerPlayer, Player targetPlayer) {
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return null;
        }
        var manager = FTBTeamsAPI.api().getManager();

        Optional<Team> attackerTeamOpt = manager.getTeamForPlayerID(attackerPlayer.getUUID());
        Optional<Team> targetTeamOpt = manager.getTeamForPlayerID(targetPlayer.getUUID());

        if (attackerTeamOpt.isPresent() && targetTeamOpt.isPresent()) {
            Team attackerTeam = attackerTeamOpt.get();
            Team targetTeam = targetTeamOpt.get();

            if (attackerTeam.getTeamId().equals(targetTeam.getTeamId())) {
                return new EntityRelations.TeamRelation(true, false);
            }

            // Check if Attacker's team considers Target an ally
            boolean attackerSeesAlly = attackerTeam.getRankForPlayer(targetPlayer.getUUID()).isAllyOrBetter();
            // Check if Target's team considers Attacker an ally
            boolean targetSeesAlly = targetTeam.getRankForPlayer(attackerPlayer.getUUID()).isAllyOrBetter();

            if (attackerSeesAlly && targetSeesAlly) {
                return new EntityRelations.TeamRelation(true, false);
            }
        }

        return null;
    }
}

