package net.spell_engine.compat;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.TeamManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.spell_engine.internals.target.EntityRelations;

public class FTBTeamsCompat {
    public static void init() {
        if (FabricLoader.getInstance().isModLoaded("ftbteams")) {
            EntityRelations.registerTeamMatcher("ftb", (attack, target) -> {
                if (attack instanceof PlayerEntity attackerPlayer && target instanceof PlayerEntity targetPlayer) {
                    boolean managerAvailable = attackerPlayer.getWorld().isClient ?
                            FTBTeamsAPI.api().isClientManagerLoaded() :
                            FTBTeamsAPI.api().isManagerLoaded();
                    if (managerAvailable) {
                        TeamManager manager = FTBTeamsAPI.api().getManager();
                        if (manager.arePlayersInSameTeam(attackerPlayer.getUuid(), targetPlayer.getUuid())) {
                            var friendlyFire = false;
                            return new EntityRelations.TeamRelation(true, friendlyFire);
                        }
                    }
                }
                return null;
            });
        }
    }
}
