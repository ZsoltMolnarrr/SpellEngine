package net.spell_engine.rpg_series.item;

import net.minecraft.entity.attribute.EntityAttributeModifier.Operation;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.spell_engine.api.config.AttributeModifier;

import java.util.List;
import java.util.function.Supplier;

/**
 * Centralized shield factory for creating standardized shields across RPG Series mods.
 * Provides tier-based durability calculation and attribute helper methods.
 *
 * <p>Usage example:
 * <pre>{@code
 * public static Shield.Entry iron_kite_shield =
 *     Shields.createStandard(
 *         "mymod",
 *         "iron_kite_shield",
 *         Equipment.Tier.TIER_1,
 *         () -> Ingredient.ofItems(Items.IRON_INGOT),
 *         MyModSounds.shield_equip.entry()
 *     );
 * }</pre>
 */
public class Shields {

    // Attribute constants
    private static final String GENERIC_ARMOR_TOUGHNESS = "minecraft:generic.armor_toughness";
    private static final String GENERIC_MAX_HEALTH = "generic.max_health";

    /**
     * Create a shield entry with tier-based durability and custom attributes.
     *
     * @param namespace        The mod namespace (e.g., "paladins")
     * @param name             The shield name (e.g., "iron_kite_shield")
     * @param tier             The shield tier (WOODEN through TIER_5, or GOLDEN)
     * @param repairIngredient Supplier for the repair ingredient
     * @param attributes       List of attribute modifiers
     * @param equipSound       Sound played when equipping shield
     * @return Shield.Entry for method chaining
     */
    public static Shield.Entry create(
            String namespace,
            String name,
            Equipment.Tier tier,
            Supplier<Ingredient> repairIngredient,
            List<AttributeModifier> attributes,
            RegistryEntry<SoundEvent> equipSound
    ) {
        var id = Identifier.of(namespace, name);
        var entry = new Shield.Entry(
                id,
                tier,
                attributes,
                repairIngredient,
                equipSound
        );

        // Set loot properties based on tier
        if (tier == Equipment.Tier.GOLDEN) {
            entry.lootProperties = Equipment.LootProperties.of("golden_weapon");
        } else {
            entry.lootProperties = Equipment.LootProperties.of(tier.getNumber());
        }

        return entry;
    }

    /**
     * Create a shield with standard tier-based attributes.
     * Automatically applies armor toughness and health bonuses based on tier.
     *
     * @param namespace        The mod namespace
     * @param name             The shield name
     * @param tier             The shield tier
     * @param repairIngredient Supplier for the repair ingredient
     * @param equipSound       Sound played when equipping shield
     * @return Shield.Entry for method chaining
     */
    public static Shield.Entry createStandard(
            String namespace,
            String name,
            Equipment.Tier tier,
            Supplier<Ingredient> repairIngredient,
            RegistryEntry<SoundEvent> equipSound
    ) {
        return create(namespace, name, tier, repairIngredient,
                standardAttributes(tier), equipSound);
    }

    /**
     * Get standard tier-based attribute modifiers for shields.
     * Returns a list of armor toughness and health bonuses appropriate for the tier.
     *
     * <p>Attribute progression:
     * <ul>
     *   <li>WOODEN, TIER_0, GOLDEN: No attributes</li>
     *   <li>TIER_1, TIER_2: +1 toughness, +2 health</li>
     *   <li>TIER_3: +1 toughness, +4 health</li>
     *   <li>TIER_4, TIER_5: +1 toughness, +6 health</li>
     * </ul>
     *
     * @param tier The shield tier
     * @return List of attribute modifiers
     */
    public static List<AttributeModifier> standardAttributes(Equipment.Tier tier) {
        return switch (tier) {
            case WOODEN, TIER_0, GOLDEN -> List.of(); // No attributes
            case TIER_1, TIER_2 -> List.of(
                    new AttributeModifier(GENERIC_ARMOR_TOUGHNESS, 1, Operation.ADD_VALUE),
                    new AttributeModifier(GENERIC_MAX_HEALTH, 2.0f, Operation.ADD_VALUE)
            );
            case TIER_3 -> List.of(
                    new AttributeModifier(GENERIC_ARMOR_TOUGHNESS, 1, Operation.ADD_VALUE),
                    new AttributeModifier(GENERIC_MAX_HEALTH, 4.0f, Operation.ADD_VALUE)
            );
            case TIER_4, TIER_5 -> List.of(
                    new AttributeModifier(GENERIC_ARMOR_TOUGHNESS, 1, Operation.ADD_VALUE),
                    new AttributeModifier(GENERIC_MAX_HEALTH, 6.0f, Operation.ADD_VALUE)
            );
        };
    }

    /**
     * Helper to create an armor toughness attribute modifier.
     *
     * @param value The toughness value to add
     * @return AttributeModifier for armor toughness
     */
    public static AttributeModifier toughness(float value) {
        return new AttributeModifier(GENERIC_ARMOR_TOUGHNESS, value, Operation.ADD_VALUE);
    }

    /**
     * Helper to create a max health attribute modifier.
     *
     * @param value The health value to add
     * @return AttributeModifier for max health
     */
    public static AttributeModifier health(float value) {
        return new AttributeModifier(GENERIC_MAX_HEALTH, value, Operation.ADD_VALUE);
    }
}
