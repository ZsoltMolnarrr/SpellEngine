package net.spell_engine.compat;

import net.combat_roll.api.event.ServerSideRollEvents;
import net.combat_roll.internals.RollingEntity;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.spell_engine.internals.SpellTriggers;

import java.util.function.Function;

public class CombatRollCompat {
    public static Function<PlayerEntity, Boolean> isRolling = player -> {
        return false;
    };

    public static void init() {
        if (FabricLoader.getInstance().isModLoaded("combat_roll")) {
            ServerSideRollEvents.PLAYER_START_ROLLING.register((player, roll) -> {
                SpellTriggers.onRoll(player);
            });

            isRolling = player -> {
                if (player instanceof RollingEntity rollingEntity) {
                    return rollingEntity.getRollManager().isRolling();
                }
                return false;
            };
        }
    }
}
