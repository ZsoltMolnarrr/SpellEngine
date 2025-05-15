package net.spell_engine.compat;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.client.KnownClientPlayer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.spell_engine.internals.target.EntityRelations;

import java.util.Optional;

public class FTBTeamsCompat {
    public static void init() {
        if (FabricLoader.getInstance().isModLoaded("ftbteams")) {
            EntityRelations.registerTeamMatcher("ftb", (attack, target) -> {
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

        return null;
    }

    private static EntityRelations.TeamRelation checkServerTeamRelation(PlayerEntity attackerPlayer, PlayerEntity targetPlayer) {
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return null;
        }
        var manager = FTBTeamsAPI.api().getManager();

        if (manager.arePlayersInSameTeam(attackerPlayer.getUuid(), targetPlayer.getUuid())) {
            return new EntityRelations.TeamRelation(true, false);
        }

        return null;
    }
}