package net.spell_engine.compat;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.client.KnownClientPlayer;
import net.minecraft.entity.player.PlayerEntity;
import net.spell_engine.Platform;
import net.spell_engine.internals.target.EntityRelations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/// FTB Teams integration: players on the same FTB team — or on two teams that consider each other allies —
/// count as teammates for spell targeting.
///
/// Optional at runtime: FTB Teams / FTB Library are `modCompileOnly` dependencies and every FTB type is
/// touched only from code reachable behind the `isModLoaded` gate below, so a client/server without FTB
/// Teams never loads a `dev.ftb.mods` class.
///
/// Pinned against FTB Teams `2001.3.2` + FTB Library `2001.2.13` (the 1.20.1 `2001.x` line on
/// `https://maven.ftb.dev/releases`); the `dev.ftb.mods.ftbteams.api` surface this uses is identical to the
/// 1.21.1 (`2101.x`) one, so this is a straight port of the 1.21.1 implementation.
public class FTBTeamsCompat {
    private static final Logger LOGGER = LoggerFactory.getLogger("SpellEngine/FTBTeamsCompat");

    public static final String MOD_ID = "ftbteams";
    public static final String MATCHER_NAME = "ftb";

    public static void init() {
        if (Platform.util().isModLoaded(MOD_ID)) {
            LOGGER.info("FTB Teams detected, registering `{}` team matcher", MATCHER_NAME);
            EntityRelations.registerTeamMatcher(MATCHER_NAME, (attack, target) -> {
                if (attack instanceof PlayerEntity attackerPlayer && target instanceof PlayerEntity targetPlayer) {
                    if (attackerPlayer.getWorld().isClient()) {
                        return checkClientTeamRelation(attackerPlayer, targetPlayer);
                    } else {
                        return checkServerTeamRelation(attackerPlayer, targetPlayer);
                    }
                }
                return null;
            });
        }
    }

    private static EntityRelations.TeamRelation checkClientTeamRelation(PlayerEntity attackerPlayer, PlayerEntity targetPlayer) {
        if (!FTBTeamsAPI.api().isClientManagerLoaded()) {
            return null;
        }
        var manager = FTBTeamsAPI.api().getClientManager();

        Optional<KnownClientPlayer> attackerKnownPlayerOpt = manager.getKnownPlayer(attackerPlayer.getUuid());
        if (attackerKnownPlayerOpt.isEmpty()) {
            return null;
        }

        Optional<KnownClientPlayer> targetKnownPlayerOpt = manager.getKnownPlayer(targetPlayer.getUuid());
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
            boolean attackerSeesAlly = attackerTeamOpt.get().getRankForPlayer(targetPlayer.getUuid()).isAllyOrBetter();
            // Check if Target's team considers Attacker an ally
            boolean targetSeesAlly = targetTeamOpt.get().getRankForPlayer(attackerPlayer.getUuid()).isAllyOrBetter();

            if (attackerSeesAlly && targetSeesAlly) {
                return new EntityRelations.TeamRelation(true, false);
            }
        }

        return null;
    }

    private static EntityRelations.TeamRelation checkServerTeamRelation(PlayerEntity attackerPlayer, PlayerEntity targetPlayer) {
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return null;
        }
        var manager = FTBTeamsAPI.api().getManager();

        Optional<Team> attackerTeamOpt = manager.getTeamForPlayerID(attackerPlayer.getUuid());
        Optional<Team> targetTeamOpt = manager.getTeamForPlayerID(targetPlayer.getUuid());

        if (attackerTeamOpt.isPresent() && targetTeamOpt.isPresent()) {
            Team attackerTeam = attackerTeamOpt.get();
            Team targetTeam = targetTeamOpt.get();

            if (attackerTeam.getTeamId().equals(targetTeam.getTeamId())) {
                return new EntityRelations.TeamRelation(true, false);
            }

            // Check if Attacker's team considers Target an ally
            boolean attackerSeesAlly = attackerTeam.getRankForPlayer(targetPlayer.getUuid()).isAllyOrBetter();
            // Check if Target's team considers Attacker an ally
            boolean targetSeesAlly = targetTeam.getRankForPlayer(attackerPlayer.getUuid()).isAllyOrBetter();

            if (attackerSeesAlly && targetSeesAlly) {
                return new EntityRelations.TeamRelation(true, false);
            }
        }

        return null;
    }
}
