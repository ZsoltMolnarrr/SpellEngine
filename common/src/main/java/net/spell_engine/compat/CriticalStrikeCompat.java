package net.spell_engine.compat;

import net.critical_strike.api.CriticalDamageSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.damage.DamageSource;

import java.util.function.BiConsumer;
import java.util.function.Predicate;

public class CriticalStrikeCompat {
    private static Predicate<DamageSource> isCriticalStrike = ds -> false;
    private static BiConsumer<DamageSource, Float> setCriticalStrike = (ds, crit) -> {
        // No-op
    };
    public static void init() {
        if (FabricLoader.getInstance().isModLoaded("critical_strike")) {
            isCriticalStrike = ds -> ((CriticalDamageSource)ds).rng_isCritical();
            setCriticalStrike = (ds, multiplier) -> ((CriticalDamageSource)ds).rng_setCriticalDamageMultiplier(multiplier);
        }
    }

    public static boolean isCriticalStrike(DamageSource damageSource) {
        if (damageSource == null) {
            return false;
        }
        return isCriticalStrike.test(damageSource);
    }

    public static void setCriticalStrike(DamageSource damageSource, float critMultiplier) {
        if (damageSource == null) {
            return;
        }
        setCriticalStrike.accept(damageSource, critMultiplier);
    }
}
