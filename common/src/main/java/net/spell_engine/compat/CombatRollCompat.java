package net.spell_engine.compat;
import net.spell_engine.Platform;

import net.combatroll.api.event.ServerSideRollEvents;
import net.combatroll.internals.RollingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.spell_engine.internals.SpellTriggers;

import java.util.function.Function;

/// Combat Roll 1.3.3+1.20.1: mod id `combatroll` and package `net.combatroll` (the 1.21 line renamed both
/// to `combat_roll`). `ServerSideRollEvents.PLAYER_START_ROLLING` hands `(ServerPlayerEntity, Vec3d)`.
///
/// Requires Combat Roll **1.3.3 or newer**: 1.3.3 moved the roll manager off `MinecraftClient` and onto the
/// player, so `RollingEntity` does not exist in 1.3.2 and older.
public class CombatRollCompat {
    public static final String MOD_ID = "combatroll";

    public static Function<PlayerEntity, Boolean> isRolling = player -> {
        return false;
    };

    public static void init() {
        if (Platform.util().isModLoaded(MOD_ID)) {
            ServerSideRollEvents.PLAYER_START_ROLLING.register((player, direction) -> {
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
