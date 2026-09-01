package net.spell_engine.compat;

import net.minecraft.world.damagesource.DamageSource;
import net.spell_engine.Platform;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * Facade over the Critical Strike integration. The implementation ({@code CriticalStrikeCompatImpl}) imports
 * Critical Strike classes and is only compiled when the Gradle property {@code enable_critical_strike} is true
 * (no Critical Strike build exists for every game version). Reached via reflection so this class never links
 * against the mod.
 */
public class CriticalStrikeCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MOD_ID = "critical_strike";
    private static final String IMPL = "net.spell_engine.compat.CriticalStrikeCompatImpl";
    public static Predicate<DamageSource> isCriticalStrike = ds -> false;
    public static BiConsumer<DamageSource, Float> setCriticalStrike = (ds, crit) -> { };

    @SuppressWarnings("unchecked")
    public static void init() {
        if (!Platform.util().isModLoaded(MOD_ID)) {
            return;
        }
        try {
            var impl = Class.forName(IMPL);
            impl.getMethod("init").invoke(null);
            isCriticalStrike = (Predicate<DamageSource>) impl.getMethod("isCriticalStrikePredicate").invoke(null);
            setCriticalStrike = (BiConsumer<DamageSource, Float>) impl.getMethod("setCriticalStrikeConsumer").invoke(null);
        } catch (ClassNotFoundException e) {
            LOGGER.warn("Critical Strike is loaded but Spell Engine was built without its compat (enable_critical_strike=false)");
        } catch (Exception e) {
            LOGGER.error("Failed to initialize Critical Strike compat", e);
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
