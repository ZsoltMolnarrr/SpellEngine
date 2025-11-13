package net.spell_engine.compat;

import net.critical_strike.api.CriticalDamageSource;
import net.critical_strike.internal.CriticalStriker;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.damage.DamageSource;
import net.spell_engine.api.spell.ExternalSpellSchools;
import net.spell_power.api.SpellSchool;

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

            // Using interface based querying instead of directly reading attributes,
            // to allow custom implementations of CriticalStriker.

            ExternalSpellSchools.PHYSICAL_RANGED.addSource(SpellSchool.Trait.CRIT_CHANCE, SpellSchool.Apply.ADD, query ->  {
                if (query.entity() instanceof CriticalStriker criticalStriker) {
                    return criticalStriker.rng_criticalChance();
                }
                return 0.0;
            });
            ExternalSpellSchools.PHYSICAL_RANGED.addSource(SpellSchool.Trait.CRIT_DAMAGE, SpellSchool.Apply.ADD, query -> {
                if (query.entity() instanceof CriticalStriker criticalStriker) {
                    return criticalStriker.rng_criticalDamageMultiplier() - 1;
                }
                return 0.0;
            });
            ExternalSpellSchools.PHYSICAL_MELEE.addSource(SpellSchool.Trait.CRIT_CHANCE, SpellSchool.Apply.ADD, query ->  {
                if (query.entity() instanceof CriticalStriker criticalStriker) {
                    return criticalStriker.rng_criticalChance();
                }
                return 0.0;
            });
            ExternalSpellSchools.PHYSICAL_MELEE.addSource(SpellSchool.Trait.CRIT_DAMAGE, SpellSchool.Apply.ADD, query -> {
                if (query.entity() instanceof CriticalStriker criticalStriker) {
                    return criticalStriker.rng_criticalDamageMultiplier() - 1;
                }
                return 0.0;
            });

//            ExternalSpellSchools.PHYSICAL_RANGED.addSource(SpellSchool.Trait.CRIT_CHANCE, SpellSchool.Apply.ADD, query ->  {
//                if (!query.entity().getAttributes().hasAttribute(CriticalStrikeAttributes.CHANCE.attributeEntry)) {
//                    return 0.0;
//                }
//                var value = query.entity().getAttributeValue(CriticalStrikeAttributes.CHANCE.attributeEntry);    // 20
//                return (double) CriticalStrikeAttributes.CHANCE.asChance(value); // 0.2
//            });
//            ExternalSpellSchools.PHYSICAL_RANGED.addSource(SpellSchool.Trait.CRIT_DAMAGE, SpellSchool.Apply.ADD, query -> {
//                if (!query.entity().getAttributes().hasAttribute(CriticalStrikeAttributes.DAMAGE.attributeEntry)) {
//                    return 0.0;
//                }
//                var value = query.entity().getAttributeValue(CriticalStrikeAttributes.DAMAGE.attributeEntry); // 150
//                return CriticalStrikeAttributes.DAMAGE.asMultiplier(value) - 1;
//            });
//            ExternalSpellSchools.PHYSICAL_MELEE.addSource(SpellSchool.Trait.CRIT_CHANCE, SpellSchool.Apply.ADD, query ->  {
//                if (!query.entity().getAttributes().hasAttribute(CriticalStrikeAttributes.CHANCE.attributeEntry)) {
//                    return 0.0;
//                }
//                var value = query.entity().getAttributeValue(CriticalStrikeAttributes.CHANCE.attributeEntry);    // 20
//                return (double) CriticalStrikeAttributes.CHANCE.asChance(value); // 0.2
//            });
//            ExternalSpellSchools.PHYSICAL_MELEE.addSource(SpellSchool.Trait.CRIT_DAMAGE, SpellSchool.Apply.ADD, query -> {
//                if (!query.entity().getAttributes().hasAttribute(CriticalStrikeAttributes.DAMAGE.attributeEntry)) {
//                    return 0.0;
//                }
//                var value = query.entity().getAttributeValue(CriticalStrikeAttributes.DAMAGE.attributeEntry); // 150
//                return CriticalStrikeAttributes.DAMAGE.asMultiplier(value) - 1;
//            });
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
