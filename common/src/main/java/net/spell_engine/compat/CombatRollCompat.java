package net.spell_engine.compat;
import net.spell_engine.Platform;

import net.combatroll.api.event.ServerSideRollEvents;
import net.combatroll.internals.RollingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.spell_engine.internals.SpellTriggers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.function.Function;

/// Combat Roll on Minecraft 1.20.1 uses mod id `combatroll` and package `net.combatroll` (the 1.21 line
/// renamed both to `combat_roll`). `ServerSideRollEvents.PLAYER_START_ROLLING` hands
/// `(ServerPlayerEntity, Vec3d)` and is present across the whole 1.20.1 line.
///
/// Reading the *current* roll state, however, moved in Combat Roll 1.3.3 ("Change mod ID, modernize API"):
///
/// | Version | Roll state holder | Query |
/// |---|---|---|
/// | 1.3.3+  | the player  | `net.combatroll.internals.RollingEntity#getRollManager().isRolling()` |
/// | ≤ 1.3.2 | the client  | `net.combatroll.client.MinecraftClientExtension#getRollManager().isRolling()` |
///
/// We compile against 1.3.3 (the version `combat_roll_version` pins) and reach the older shape by
/// reflection: Combat Roll's own class names survive remapping, so only the *holder* differs. Without the
/// fallback a 1.3.2 jar throws `NoClassDefFoundError: net/combatroll/internals/RollingEntity` on every
/// client tick, because the lambda below resolves that class lazily rather than at registration.
public class CombatRollCompat {
    private static final Logger LOGGER = LoggerFactory.getLogger("SpellEngine/CombatRollCompat");
    public static final String MOD_ID = "combatroll";
    private static final String ROLLING_ENTITY = "net.combatroll.internals.RollingEntity";
    private static final String LEGACY_CLIENT_EXTENSION = "net.combatroll.client.MinecraftClientExtension";

    /// Roll state of a player, on Combat Roll 1.3.3+. Stays `false` on older versions — use
    /// [#isRolling(PlayerEntity, Object)] from client code so the 1.3.2 fallback is consulted too.
    public static Function<PlayerEntity, Boolean> isRolling = player -> {
        return false;
    };

    private static Method legacyGetRollManager;
    private static Method legacyIsRolling;

    /// Is this player mid-roll? `minecraftClient` is the `MinecraftClient` instance, consulted only by the
    /// Combat Roll ≤1.3.2 fallback, where the roll manager hangs off the client instead of the player.
    /// That older state is per-client, so it can only describe the local player — which is the only player
    /// the caller (the client-side spell hotbar) ever asks about.
    public static boolean isRolling(PlayerEntity player, Object minecraftClient) {
        if (isRolling.apply(player)) {
            return true;
        }
        return legacyIsRolling(minecraftClient);
    }

    public static void init() {
        if (Platform.util().isModLoaded(MOD_ID)) {
            ServerSideRollEvents.PLAYER_START_ROLLING.register((player, direction) -> {
                SpellTriggers.onRoll(player);
            });

            if (isClassPresent(ROLLING_ENTITY)) {
                isRolling = player -> {
                    if (player instanceof RollingEntity rollingEntity) {
                        return rollingEntity.getRollManager().isRolling();
                    }
                    return false;
                };
                return;
            }

            try {
                var extension = Class.forName(LEGACY_CLIENT_EXTENSION, false, CombatRollCompat.class.getClassLoader());
                legacyGetRollManager = extension.getMethod("getRollManager");
                LOGGER.info("Combat Roll predates 1.3.3 — reading roll state from the client-side API.");
            } catch (ClassNotFoundException | NoSuchMethodException | LinkageError e) {
                LOGGER.warn("Combat Roll is present but exposes neither `{}` nor `{}` — spell casting will "
                        + "not be interrupted by rolling.", ROLLING_ENTITY, LEGACY_CLIENT_EXTENSION);
            }
        }
    }

    private static boolean legacyIsRolling(Object minecraftClient) {
        if (legacyGetRollManager == null || minecraftClient == null) {
            return false;
        }
        try {
            var rollManager = legacyGetRollManager.invoke(minecraftClient);
            if (rollManager == null) {
                return false;
            }
            if (legacyIsRolling == null) {
                legacyIsRolling = rollManager.getClass().getMethod("isRolling");
            }
            return (Boolean) legacyIsRolling.invoke(rollManager);
        } catch (ReflectiveOperationException | ClassCastException | LinkageError e) {
            // One failure means the shape is not what we assumed; stop trying instead of logging per tick.
            legacyGetRollManager = null;
            LOGGER.warn("Combat Roll's client-side roll API did not respond as expected — "
                    + "spell casting will not be interrupted by rolling.", e);
            return false;
        }
    }

    private static boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, CombatRollCompat.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }
}
