package net.spell_engine.rpg_series.item;

import net.fabric_extras.ranged_weapon.api.CustomBow;
import net.fabric_extras.ranged_weapon.api.CustomCrossbow;
import net.fabric_extras.ranged_weapon.api.RangedConfig;
import net.minecraft.recipe.Ingredient;
import net.minecraft.util.Identifier;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Centralized ranged weapon factory for creating standardized bows and crossbows across RPG Series mods.
 * Provides tier-based damage calculation, automatic pull time/velocity assignment, and durability handling.
 *
 * <p>Usage example:
 * <pre>{@code
 * public static RangedWeapon.Entry netherite_longbow =
 *     RangedWeapons.longBow(
 *         "mymod",
 *         "netherite_longbow",
 *         Equipment.Tier.TIER_3,
 *         () -> Ingredient.ofItems(Items.NETHERITE_INGOT)
 *     );
 * }</pre>
 */
public class RangedWeapons {

    // ===== DAMAGE MAPPING =====

    /**
     * Helper class to store tier-to-damage mappings for a ranged weapon type.
     */
    private static class DamageMap {
        private final Map<Equipment.Tier, Float> values = new EnumMap<>(Equipment.Tier.class);

        static Builder builder() {
            return new Builder();
        }

        Float get(Equipment.Tier tier) {
            return values.get(tier);
        }

        static class Builder {
            private final Map<Equipment.Tier, Float> values = new EnumMap<>(Equipment.Tier.class);

            Builder wooden(float damage) {
                values.put(Equipment.Tier.WOODEN, damage);
                return this;
            }

            Builder t0(float damage) {
                values.put(Equipment.Tier.TIER_0, damage);
                return this;
            }

            Builder t1(float damage) {
                values.put(Equipment.Tier.TIER_1, damage);
                return this;
            }

            Builder t2(float damage) {
                values.put(Equipment.Tier.TIER_2, damage);
                return this;
            }

            Builder t3(float damage) {
                values.put(Equipment.Tier.TIER_3, damage);
                return this;
            }

            Builder t4(float damage) {
                values.put(Equipment.Tier.TIER_4, damage);
                return this;
            }

            Builder t5(float damage) {
                values.put(Equipment.Tier.TIER_5, damage);
                return this;
            }

            Builder golden(float damage) {
                values.put(Equipment.Tier.GOLDEN, damage);
                return this;
            }

            DamageMap build() {
                var map = new DamageMap();
                map.values.putAll(values);
                return map;
            }
        }
    }

    private static final Map<Equipment.WeaponType, DamageMap> DAMAGE_MAPS = new EnumMap<>(Equipment.WeaponType.class);
    private static final Map<Equipment.WeaponType, String> WEAPON_ATTRIBUTES = new EnumMap<>(Equipment.WeaponType.class);

    // ===== PULL TIME AND VELOCITY CONSTANTS =====

    // Pull time in seconds, with 1 sec offset
    private static final float PULL_TIME_SHORT_BOW = -0.2F;
    private static final float PULL_TIME_LONG_BOW = 0.5F;
    private static final float PULL_TIME_RAPID_CROSSBOW = 0F;
    private static final float PULL_TIME_HEAVY_CROSSBOW = 0.75F;

    // Velocity bonus (projectile speed addition)
    private static final float VELOCITY_SHORT_BOW = 0F;
    private static final float VELOCITY_LONG_BOW = 0.75F;
    private static final float VELOCITY_RAPID_CROSSBOW = 0F;
    private static final float VELOCITY_HEAVY_CROSSBOW = 0.5F;

    // ===== STATIC INITIALIZATION =====

    static {
        // Initialize damage maps for all ranged weapon types
        DAMAGE_MAPS.put(Equipment.WeaponType.SHORT_BOW, DamageMap.builder()
                .wooden(6F).t0(7F).t1(7.5F).t2(8F).t3(9F).t4(10F).t5(10F).golden(6.5F)
                .build());

        DAMAGE_MAPS.put(Equipment.WeaponType.LONG_BOW, DamageMap.builder()
                .wooden(6F).t0(7F).t1(8F).t2(10F).t3(12F).t4(13.5F).t5(13.5F).golden(7F)
                .build());

        // Crossbows start at T1 (no wooden/T0 crossbows exist in vanilla or mods)
        DAMAGE_MAPS.put(Equipment.WeaponType.RAPID_CROSSBOW, DamageMap.builder()
                .t1(7.5F).t2(8.5F).t3(9.5F).t4(10.5F).t5(10.5F).golden(6F)
                .build());

        DAMAGE_MAPS.put(Equipment.WeaponType.HEAVY_CROSSBOW, DamageMap.builder()
                .t1(11F).t2(13F).t3(15F).t4(17F).t5(17F).golden(9F)
                .build());

        WEAPON_ATTRIBUTES.put(Equipment.WeaponType.LONG_BOW, "bow_two_handed_heavy");
        WEAPON_ATTRIBUTES.put(Equipment.WeaponType.HEAVY_CROSSBOW, "crossbow_two_handed_heavy");
    }

    // ===== PUBLIC FACTORY METHODS =====

    /**
     * Create a ranged weapon entry with automatic damage calculation from tier.
     *
     * @param namespace        The mod namespace (e.g., "archers")
     * @param name             The weapon name (e.g., "netherite_longbow")
     * @param weaponType       The weapon type (SHORT_BOW, LONG_BOW, RAPID_CROSSBOW, HEAVY_CROSSBOW)
     * @param tier             The weapon tier (WOODEN through TIER_5, or GOLDEN)
     * @param repairIngredient Supplier for the repair ingredient
     * @param pullTime         The pull/charge time for the weapon
     * @param velocity         The projectile velocity multiplier
     * @param factory          The ranged weapon factory (CustomBow::new or CustomCrossbow::new)
     * @return RangedWeapon.Entry for method chaining
     */
    public static RangedWeapon.Entry create(
            String namespace,
            String name,
            Equipment.WeaponType weaponType,
            Equipment.Tier tier,
            Supplier<Ingredient> repairIngredient,
            float pullTime,
            float velocity,
            RangedWeapon.RangedFactory factory
    ) {
        // Get damage from map
        var damageMap = DAMAGE_MAPS.get(weaponType);
        if (damageMap == null) {
            throw new IllegalArgumentException("No damage map configured for weapon type: " + weaponType);
        }
        var damage = damageMap.get(tier);
        if (damage == null) {
            throw new IllegalArgumentException("Tier " + tier + " not available for weapon type: " + weaponType);
        }

        // Create RangedConfig
        var config = new RangedConfig(damage, pullTime, velocity);

        // Create entry (RangedWeapon.Entry handles durability automatically via tier)
        var id = Identifier.of(namespace, name);
        var entry = new RangedWeapon.Entry(id, tier, factory, config, repairIngredient, weaponType);
        entry.weaponAttributesPreset = WEAPON_ATTRIBUTES.getOrDefault(weaponType, "");

        return entry;
    }

    // ===== WEAPON-TYPE-SPECIFIC HELPER METHODS =====

    /**
     * Create a short bow with automatic tier-based configuration.
     * Pull time: -0.2F, Velocity: 0F
     */
    public static RangedWeapon.Entry shortBow(
            String namespace,
            String name,
            Equipment.Tier tier,
            Supplier<Ingredient> repairIngredient
    ) {
        return create(namespace, name, Equipment.WeaponType.SHORT_BOW, tier, repairIngredient,
                PULL_TIME_SHORT_BOW, VELOCITY_SHORT_BOW, CustomBow::new);
    }

    /**
     * Create a long bow with automatic tier-based configuration.
     * Pull time: -0.5F, Velocity: 0.75F
     */
    public static RangedWeapon.Entry longBow(
            String namespace,
            String name,
            Equipment.Tier tier,
            Supplier<Ingredient> repairIngredient
    ) {
        return create(namespace, name, Equipment.WeaponType.LONG_BOW, tier, repairIngredient,
                PULL_TIME_LONG_BOW, VELOCITY_LONG_BOW, CustomBow::new);
    }

    /**
     * Create a rapid crossbow with automatic tier-based configuration.
     * Pull time: 0F (instant), Velocity: 0F
     */
    public static RangedWeapon.Entry rapidCrossbow(
            String namespace,
            String name,
            Equipment.Tier tier,
            Supplier<Ingredient> repairIngredient
    ) {
        return create(namespace, name, Equipment.WeaponType.RAPID_CROSSBOW, tier, repairIngredient,
                PULL_TIME_RAPID_CROSSBOW, VELOCITY_RAPID_CROSSBOW, CustomCrossbow::new);
    }

    /**
     * Create a heavy crossbow with automatic tier-based configuration.
     * Pull time: -0.75F, Velocity: 0.5F
     */
    public static RangedWeapon.Entry heavyCrossbow(
            String namespace,
            String name,
            Equipment.Tier tier,
            Supplier<Ingredient> repairIngredient
    ) {
        return create(namespace, name, Equipment.WeaponType.HEAVY_CROSSBOW, tier, repairIngredient,
                PULL_TIME_HEAVY_CROSSBOW, VELOCITY_HEAVY_CROSSBOW, CustomCrossbow::new);
    }
}
