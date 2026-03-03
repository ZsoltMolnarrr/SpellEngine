package net.spell_engine.rpg_series.item;

import net.minecraft.recipe.Ingredient;
import net.minecraft.util.Identifier;
import net.spell_engine.api.config.AttributeModifier;
import net.spell_engine.api.config.WeaponConfig;
import net.spell_engine.api.item.weapon.SpellSwordItem;
import net.spell_engine.api.item.weapon.SpellWeaponItem;
import net.spell_engine.api.item.weapon.StaffItem;
import net.spell_engine.api.spell.container.SpellContainer;
import net.spell_engine.api.spell.container.SpellContainers;
import net.spell_engine.rpg_series.datagen.WeaponSkills;
import net.spell_power.api.SpellSchools;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Centralized weapon factory for creating standardized weapons across RPG Series mods.
 * Provides tier-based damage calculation, automatic attack speed assignment, and spell power bonuses.
 */
public class Weapons {

    // ===== DAMAGE MAPPING =====
    /**
     * Helper class to store tier-to-damage mappings for a weapon type.
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

    // ===== ATTACK SPEED MAPPING =====

    private static final Map<Equipment.WeaponType, Float> ATTACK_SPEEDS = new EnumMap<>(Equipment.WeaponType.class);

    // ===== SPELL POWER MAPPING =====

    /**
     * Helper class to store tier-to-spell-power mappings for magical weapons.
     */
    private static class SpellPowerMap {
        private final Map<Equipment.Tier, Float> values = new EnumMap<>(Equipment.Tier.class);

        static Builder builder() {
            return new Builder();
        }

        Float get(Equipment.Tier tier) {
            return values.get(tier);
        }

        static class Builder {
            private final Map<Equipment.Tier, Float> values = new EnumMap<>(Equipment.Tier.class);

            Builder wooden(float power) {
                values.put(Equipment.Tier.WOODEN, power);
                return this;
            }

            Builder t0(float power) {
                values.put(Equipment.Tier.TIER_0, power);
                return this;
            }

            Builder t1(float power) {
                values.put(Equipment.Tier.TIER_1, power);
                return this;
            }

            Builder t2(float power) {
                values.put(Equipment.Tier.TIER_2, power);
                return this;
            }

            Builder t3(float power) {
                values.put(Equipment.Tier.TIER_3, power);
                return this;
            }

            Builder t4(float power) {
                values.put(Equipment.Tier.TIER_4, power);
                return this;
            }

            Builder t5(float power) {
                values.put(Equipment.Tier.TIER_5, power);
                return this;
            }

            Builder golden(float power) {
                values.put(Equipment.Tier.GOLDEN, power);
                return this;
            }

            SpellPowerMap build() {
                var map = new SpellPowerMap();
                map.values.putAll(values);
                return map;
            }
        }
    }

    private static final Map<Equipment.WeaponType, SpellPowerMap> SPELL_POWER_MAPS = new EnumMap<>(Equipment.WeaponType.class);
    private static final Map<Equipment.WeaponType, String> WEAPON_ATTRIBUTES = new EnumMap<>(Equipment.WeaponType.class);

    // ===== STATIC INITIALIZATION =====

    static {
        // Initialize damage maps for all weapon types
        DAMAGE_MAPS.put(Equipment.WeaponType.CLAYMORE, DamageMap.builder()
                .wooden(5.3F).t0(6.8F).t1(8.3F).t2(9.9F).t3(11.5F).t4(13F).t5(13F).golden(5.2F).build());

        DAMAGE_MAPS.put(Equipment.WeaponType.HAMMER, DamageMap.builder()
                .wooden(6.6F).t0(8.5F).t1(10.3F).t2(12.2F).t3(14.1F).t4(16F).t5(16F).golden(6.6F).build());

        DAMAGE_MAPS.put(Equipment.WeaponType.MACE, DamageMap.builder()
                .wooden(4.4F).t0(5.7F).t1(7F).t2(8.3F).t3(9.6F).t4(11F).t5(11F).golden(4.3F).build());

        DAMAGE_MAPS.put(Equipment.WeaponType.SPEAR, DamageMap.builder()
                .wooden(3F).t0(4F).t1(5F).t2(6F).t3(7F).t4(8F).t5(8F).golden(3F).build());

        DAMAGE_MAPS.put(Equipment.WeaponType.DAGGER, DamageMap.builder()
                .wooden(2F).t0(2.6F).t1(3.3F).t2(4F).t3(4.7F).t4(5.5F).t5(5.5F).golden(1.8F).build());

        DAMAGE_MAPS.put(Equipment.WeaponType.SICKLE, DamageMap.builder()
                .wooden(2.4F).t0(3.2F).t1(4.1F).t2(5F).t3(5.9F).t4(6.8F).t5(6.8F).golden(2.4F).build());

        DAMAGE_MAPS.put(Equipment.WeaponType.DOUBLE_AXE, DamageMap.builder()
                .wooden(4.4F).t0(5.6F).t1(7F).t2(8.3F).t3(9.6F).t4(11F).t5(11F).golden(4.3F).build());

        DAMAGE_MAPS.put(Equipment.WeaponType.GLAIVE, DamageMap.builder()
                .wooden(3.5F).t0(4.6F).t1(5.8F).t2(7F).t3(8.1F).t4(9.3F).t5(9.3F).golden(3.5F).build());

        DAMAGE_MAPS.put(Equipment.WeaponType.SWORD, DamageMap.builder()
                .wooden(3F).t0(4F).t1(5F).t2(6F).t3(7F).t4(8F).t5(8F).golden(3F).build());

        // Wands and staves have constant damage regardless of tier
        DAMAGE_MAPS.put(Equipment.WeaponType.DAMAGE_WAND, DamageMap.builder()
                .wooden(2F).t0(2F).t1(2F).t2(2F).t3(2F).t4(2F).t5(2F).golden(2F).build());

        DAMAGE_MAPS.put(Equipment.WeaponType.HEALING_WAND, DamageMap.builder()
                .wooden(2F).t0(2F).t1(2F).t2(2F).t3(2F).t4(2F).t5(2F).golden(2F).build());

        DAMAGE_MAPS.put(Equipment.WeaponType.DAMAGE_STAFF, DamageMap.builder()
                .wooden(4F).t0(4F).t1(4F).t2(4F).t3(4F).t4(4F).t5(4F).golden(4F).build());

        DAMAGE_MAPS.put(Equipment.WeaponType.HEALING_STAFF, DamageMap.builder()
                .wooden(4F).t0(4F).t1(4F).t2(4F).t3(4F).t4(4F).t5(4F).golden(4F).build());

        // Initialize attack speeds
        ATTACK_SPEEDS.put(Equipment.WeaponType.DAGGER, -1.6F);
        ATTACK_SPEEDS.put(Equipment.WeaponType.SICKLE, -2.0F);
        ATTACK_SPEEDS.put(Equipment.WeaponType.DAMAGE_WAND, -2.4F);
        ATTACK_SPEEDS.put(Equipment.WeaponType.HEALING_WAND, -2.4F);
        ATTACK_SPEEDS.put(Equipment.WeaponType.SWORD, -2.4F);
        ATTACK_SPEEDS.put(Equipment.WeaponType.SPEAR, -2.6F);
        ATTACK_SPEEDS.put(Equipment.WeaponType.GLAIVE, -2.6F);
        ATTACK_SPEEDS.put(Equipment.WeaponType.MACE, -2.8F);
        ATTACK_SPEEDS.put(Equipment.WeaponType.DOUBLE_AXE, -2.8F);
        ATTACK_SPEEDS.put(Equipment.WeaponType.CLAYMORE, -3.0F);
        ATTACK_SPEEDS.put(Equipment.WeaponType.DAMAGE_STAFF, -3.0F);
        ATTACK_SPEEDS.put(Equipment.WeaponType.HEALING_STAFF, -3.0F);
        ATTACK_SPEEDS.put(Equipment.WeaponType.HAMMER, -3.2F);

        // Initialize weapon attribute presets
        WEAPON_ATTRIBUTES.put(Equipment.WeaponType.DAGGER, "dagger");
        WEAPON_ATTRIBUTES.put(Equipment.WeaponType.SICKLE, "sickle");
        WEAPON_ATTRIBUTES.put(Equipment.WeaponType.DAMAGE_WAND, "wand");
        WEAPON_ATTRIBUTES.put(Equipment.WeaponType.HEALING_WAND, "wand");
        WEAPON_ATTRIBUTES.put(Equipment.WeaponType.SWORD, "sword");
        WEAPON_ATTRIBUTES.put(Equipment.WeaponType.SPEAR, "spear");
        WEAPON_ATTRIBUTES.put(Equipment.WeaponType.GLAIVE, "glaive");
        WEAPON_ATTRIBUTES.put(Equipment.WeaponType.MACE, "mace");
        WEAPON_ATTRIBUTES.put(Equipment.WeaponType.DOUBLE_AXE, "double_axe");
        WEAPON_ATTRIBUTES.put(Equipment.WeaponType.CLAYMORE, "claymore");
        WEAPON_ATTRIBUTES.put(Equipment.WeaponType.DAMAGE_STAFF, "staff");
        WEAPON_ATTRIBUTES.put(Equipment.WeaponType.HEALING_STAFF, "staff");
        WEAPON_ATTRIBUTES.put(Equipment.WeaponType.HAMMER, "hammer");


        // Initialize spell power maps for wands and staves
        var wandPower = SpellPowerMap.builder()
                .wooden(2.5F).t0(3F).t1(4F).t2(5F).t3(5.5F).t4(8F).t5(8F).golden(3F).build();
        SPELL_POWER_MAPS.put(Equipment.WeaponType.DAMAGE_WAND, wandPower);
        SPELL_POWER_MAPS.put(Equipment.WeaponType.HEALING_WAND, wandPower);

        var staffPower = SpellPowerMap.builder()
                .wooden(4F).t0(4.5F).t1(5F).t2(6F).t3(7F).t4(8F).t5(8F).golden(5F).build();
        SPELL_POWER_MAPS.put(Equipment.WeaponType.DAMAGE_STAFF, staffPower);
        SPELL_POWER_MAPS.put(Equipment.WeaponType.HEALING_STAFF, staffPower);
    }

    // ===== PUBLIC FACTORY METHODS =====

    /**
     * Create a weapon entry with automatic damage calculation from tier.
     *
     * @param namespace        The mod namespace (e.g., "paladins")
     * @param name             The weapon name (e.g., "iron_claymore")
     * @param weaponType       The weapon type from Equipment.WeaponType enum
     * @param tier             The weapon tier (TIER_0 through TIER_4, or GOLDEN)
     * @param repairIngredient Supplier for the repair ingredient
     * @return Weapon.Entry for method chaining
     */
    public static Weapon.Entry create(
            String namespace,
            String name,
            Equipment.WeaponType weaponType,
            Equipment.Tier tier,
            Supplier<Ingredient> repairIngredient
    ) {
        // Get damage from damage map
        var damageMap = DAMAGE_MAPS.get(weaponType);
        if (damageMap == null) {
            throw new IllegalArgumentException("No damage map configured for weapon type: " + weaponType);
        }
        var damage = damageMap.get(tier);
        if (damage == null) {
            throw new IllegalArgumentException("Tier " + tier + " not available for weapon type: " + weaponType);
        }

        // Get attack speed
        var attackSpeed = ATTACK_SPEEDS.get(weaponType);
        if (attackSpeed == null) {
            throw new IllegalArgumentException("No attack speed configured for weapon type: " + weaponType);
        }

        // Create material
        var material = Weapon.CustomMaterial.matching(tier.getVanillaMaterial(), repairIngredient);

        // Create weapon config
        var config = new WeaponConfig(damage, attackSpeed);

        // Select appropriate factory
        var factory = getFactory(weaponType);

        // Create entry
        var entry = new Weapon.Entry(namespace, name, material, factory, config, weaponType);

        entry.weaponAttributesPreset = WEAPON_ATTRIBUTES.getOrDefault(weaponType, "");

        // Apply loot properties with tier
        if (tier == Equipment.Tier.GOLDEN) {
            entry.loot(Equipment.LootProperties.of("golden_weapon"));
        } else {
            entry.loot(Equipment.LootProperties.of(tier.getNumber()));
        }

        return entry;
    }

    // ===== WEAPON-TYPE-SPECIFIC HELPER METHODS =====

    // == MELEE WEAPONS ==

    // Sword

    public static Weapon.Entry sword(String namespace, String name, Equipment.Tier tier, Supplier<Ingredient> repairIngredient) {
        return create(namespace, name, Equipment.WeaponType.SWORD, tier, repairIngredient);
    }
    public static Weapon.Entry swordWithSkill(String namespace, String name, Equipment.Tier tier, Supplier<Ingredient> repairIngredient) {
        var entry = sword(namespace, name, tier, repairIngredient);
        return entry.spellContainer(SWORD_CONTAINER);
    }
    public static final SpellContainer SWORD_CONTAINER = SpellContainers.forMeleeWeapon().withSpellId(WeaponSkills.SWIFT_STRIKES.id());

    // Claymore

    public static Weapon.Entry claymore(String namespace, String name, Equipment.Tier tier, Supplier<Ingredient> repairIngredient) {
        return create(namespace, name, Equipment.WeaponType.CLAYMORE, tier, repairIngredient);
    }
    public static Weapon.Entry claymoreWithSkill(String namespace, String name, Equipment.Tier tier, Supplier<Ingredient> repairIngredient) {
        var entry = claymore(namespace, name, tier, repairIngredient);
        return entry.spellContainer(CLAYMORE_CONTAINER);
    }
    public static final SpellContainer CLAYMORE_CONTAINER = SpellContainers.forMeleeWeapon().withSpellId(WeaponSkills.FLURRY.id());

    // Mace

    public static Weapon.Entry mace(String namespace, String name, Equipment.Tier tier, Supplier<Ingredient> repairIngredient) {
        return create(namespace, name, Equipment.WeaponType.MACE, tier, repairIngredient);
    }
    public static Weapon.Entry maceWithSkill(String namespace, String name, Equipment.Tier tier, Supplier<Ingredient> repairIngredient) {
        var entry = mace(namespace, name, tier, repairIngredient);
        return entry.spellContainer(MACE_CONTAINER);
    }
    public static final SpellContainer MACE_CONTAINER = SpellContainers.forMeleeWeapon().withSpellId(WeaponSkills.SMASH.id());

    // Hammer

    public static Weapon.Entry hammer(String namespace, String name, Equipment.Tier tier, Supplier<Ingredient> repairIngredient) {
        return create(namespace, name, Equipment.WeaponType.HAMMER, tier, repairIngredient);
    }
    public static Weapon.Entry hammerWithSkill(String namespace, String name, Equipment.Tier tier, Supplier<Ingredient> repairIngredient) {
        var entry = hammer(namespace, name, tier, repairIngredient);
        return entry.spellContainer(HAMMER_CONTAINER);
    }
    public static final SpellContainer HAMMER_CONTAINER = SpellContainers.forMeleeWeapon().withSpellId(WeaponSkills.GROUND_SLAM.id());

    // Axe

    public static final SpellContainer AXE_CONTAINER = SpellContainers.forMeleeWeapon().withSpellId(WeaponSkills.CLEAVE.id());

    // Double Axe

    public static Weapon.Entry doubleAxe(String namespace, String name, Equipment.Tier tier, Supplier<Ingredient> repairIngredient) {
        return create(namespace, name, Equipment.WeaponType.DOUBLE_AXE, tier, repairIngredient);
    }
    public static Weapon.Entry doubleAxeWithSkill(String namespace, String name, Equipment.Tier tier, Supplier<Ingredient> repairIngredient) {
        var entry = doubleAxe(namespace, name, tier, repairIngredient);
        return entry.spellContainer(DOUBLE_AXE_CONTAINER);
    }
    public static final SpellContainer DOUBLE_AXE_CONTAINER = SpellContainers.forMeleeWeapon().withSpellId(WeaponSkills.WHIRLWIND.id());

    // Spear

    public static Weapon.Entry spear(String namespace, String name, Equipment.Tier tier, Supplier<Ingredient> repairIngredient) {
        return create(namespace, name, Equipment.WeaponType.SPEAR, tier, repairIngredient);
    }
    public static Weapon.Entry spearWithSkill(String namespace, String name, Equipment.Tier tier, Supplier<Ingredient> repairIngredient) {
        var entry = spear(namespace, name, tier, repairIngredient);
        return entry.spellContainer(SPEAR_CONTAINER);
    }
    public static final SpellContainer SPEAR_CONTAINER = SpellContainers.forMeleeWeapon().withSpellId(WeaponSkills.IMPALE.id());

    // Dagger

    public static Weapon.Entry dagger(String namespace, String name, Equipment.Tier tier, Supplier<Ingredient> repairIngredient) {
        return create(namespace, name, Equipment.WeaponType.DAGGER, tier, repairIngredient);
    }
    public static Weapon.Entry daggerWithSkill(String namespace, String name, Equipment.Tier tier, Supplier<Ingredient> repairIngredient) {
        var entry = dagger(namespace, name, tier, repairIngredient);
        return entry.spellContainer(DAGGER_CONTAINER);
    }
    public static final SpellContainer DAGGER_CONTAINER = SpellContainers.forMeleeWeapon().withSpellId(WeaponSkills.FAN_OF_KNIVES.id());

    // Sickle

    public static Weapon.Entry sickle(String namespace, String name, Equipment.Tier tier, Supplier<Ingredient> repairIngredient) {
        return create(namespace, name, Equipment.WeaponType.SICKLE, tier, repairIngredient);
    }
    public static Weapon.Entry sickleWithSkill(String namespace, String name, Equipment.Tier tier, Supplier<Ingredient> repairIngredient) {
        var entry = sickle(namespace, name, tier, repairIngredient);
        return entry.spellContainer(SICKLE_CONTAINER);
    }
    public static final SpellContainer SICKLE_CONTAINER = SpellContainers.forMeleeWeapon().withSpellId(WeaponSkills.SWIPE.id());

    // Glaive

    public static Weapon.Entry glaive(String namespace, String name, Equipment.Tier tier, Supplier<Ingredient> repairIngredient) {
        return create(namespace, name, Equipment.WeaponType.GLAIVE, tier, repairIngredient);
    }
    public static Weapon.Entry glaiveWithSkill(String namespace, String name, Equipment.Tier tier, Supplier<Ingredient> repairIngredient) {
        var entry = glaive(namespace, name, tier, repairIngredient);
        return entry.spellContainer(GLAIVE_CONTAINER);
    }
    public static final SpellContainer GLAIVE_CONTAINER = SpellContainers.forMeleeWeapon().withSpellId(WeaponSkills.THRUST.id());

    // == MAGICAL WEAPONS ==

    public static Weapon.Entry damageWand(String namespace, String name, Equipment.Tier tier, Supplier<Ingredient> repairIngredient, List<Identifier> spellSchools) {
        var entry = create(namespace, name, Equipment.WeaponType.DAMAGE_WAND, tier, repairIngredient);
        applySpellPower(entry, Equipment.WeaponType.DAMAGE_WAND, tier, spellSchools);
        return entry;
    }

    public static Weapon.Entry damageStaff(String namespace, String name, Equipment.Tier tier, Supplier<Ingredient> repairIngredient, List<Identifier> spellSchools) {
        var entry = create(namespace, name, Equipment.WeaponType.DAMAGE_STAFF, tier, repairIngredient);
        applySpellPower(entry, Equipment.WeaponType.DAMAGE_STAFF, tier, spellSchools);
        return entry;
    }

    public static Weapon.Entry healingWand(String namespace, String name, Equipment.Tier tier, Supplier<Ingredient> repairIngredient) {
        var entry = create(namespace, name, Equipment.WeaponType.HEALING_WAND, tier, repairIngredient);
        applySpellPower(entry, Equipment.WeaponType.HEALING_WAND, tier, List.of(SpellSchools.HEALING.id));
        return entry;
    }

    public static Weapon.Entry healingStaff(String namespace, String name, Equipment.Tier tier, Supplier<Ingredient> repairIngredient) {
        var entry = create(namespace, name, Equipment.WeaponType.HEALING_STAFF, tier, repairIngredient);
        applySpellPower(entry, Equipment.WeaponType.HEALING_STAFF, tier, List.of(SpellSchools.HEALING.id));
        return entry;
    }

    public static Weapon.Entry healingStaff(String namespace, String name, Equipment.Tier tier, Supplier<Ingredient> repairIngredient, List<Identifier> spellSchools) {
        var entry = create(namespace, name, Equipment.WeaponType.HEALING_STAFF, tier, repairIngredient);
        applySpellPower(entry, Equipment.WeaponType.HEALING_STAFF, tier, spellSchools);
        return entry;
    }
    
    // ===== PRIVATE HELPER METHODS =====

    /**
     * Get the appropriate factory class for a weapon type.
     */
    public static Weapon.Factory getFactory(Equipment.WeaponType weaponType) {
        return switch (weaponType) {
            case DAMAGE_STAFF, HEALING_STAFF, DAMAGE_WAND, HEALING_WAND -> StaffItem::new;
            case CLAYMORE, DAGGER, SICKLE, DOUBLE_AXE, GLAIVE, SWORD, SPELL_BLADE, SPELL_SCYTHE -> SpellSwordItem::new;
            case HAMMER, MACE, SPEAR -> SpellWeaponItem::new;
            case SHIELD, SHORT_BOW, LONG_BOW, RAPID_CROSSBOW, HEAVY_CROSSBOW -> throw new IllegalArgumentException("Type not supported for weapon creation");
        };
    }

    /**
     * Apply spell power bonuses to custom spell schools.
     * Used for damage wands/staves with specific spell school configurations.
     */
    private static void applySpellPower(
            Weapon.Entry entry,
            Equipment.WeaponType weaponType,
            Equipment.Tier tier,
            List<Identifier> spellAttributes
    ) {
        var spellPowerMap = SPELL_POWER_MAPS.get(weaponType);
        if (spellPowerMap == null) {
            return; // Not a magical weapon
        }

        var spellPower = spellPowerMap.get(tier);
        if (spellPower == null) {
            return; // Tier not configured for spell power
        }

        // Apply spell power to each specified school
        for (var attributeId : spellAttributes) {
            entry.attribute(AttributeModifier.bonus(attributeId, spellPower));
        }
    }
}
