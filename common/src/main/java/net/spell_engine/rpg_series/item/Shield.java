package net.spell_engine.rpg_series.item;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.minecraft.util.Rarity;
import net.minecraft.util.Util;
import net.spell_engine.api.config.AttributeModifier;
import net.spell_engine.api.config.ShieldConfig;
import net.spell_engine.api.spell.SpellDataComponents;
import net.spell_engine.api.spell.container.SpellChoice;
import net.spell_engine.api.spell.container.SpellContainer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Shield API providing entry class and registration system.
 * Remains independent from fabric-extras shield library.
 */
public class Shield {

    /**
     * Generic shield factory interface that doesn't depend on fabric-extras.
     * Implementations will provide the actual shield item creation logic (e.g., CustomShieldItem::new).
     */
    public interface ShieldFactory {
        Item create(
                RegistryEntry<SoundEvent> equipSound,
                Supplier<Ingredient> repairIngredient,
                List<Pair<RegistryEntry<EntityAttribute>, EntityAttributeModifier>> attributes,
                Item.Settings settings
        );
    }

    /**
     * Shield entry class that stores shield configuration and handles item creation.
     * Does NOT store the factory to remain independent from fabric-extras.
     */
    public static final class Entry {
        private final Identifier id;
        private final Equipment.Tier tier;
        private final List<AttributeModifier> defaults;
        private final Supplier<Ingredient> repairIngredientSupplier;
        private final RegistryEntry<SoundEvent> equipSound;

        private String translatedName = "";
        public Rarity rarity = Rarity.COMMON;
        @Nullable private Item registeredItem;

        @Nullable public SpellChoice spellChoice;
        @Nullable public SpellContainer spellContainer;

        public Equipment.WeaponType category = Equipment.WeaponType.LONG_BOW;
        public Equipment.LootProperties lootProperties = Equipment.LootProperties.EMPTY;

        public Entry(
                Identifier id,
                Equipment.Tier tier,
                List<AttributeModifier> defaults,
                Supplier<Ingredient> repairIngredientSupplier,
                RegistryEntry<SoundEvent> equipSound
        ) {
            this.id = id;
            this.tier = tier;
            this.lootProperties = Equipment.LootProperties.of(tier.getNumber());
            this.defaults = defaults;
            this.repairIngredientSupplier = repairIngredientSupplier;
            this.equipSound = equipSound;
        }

        // Getters

        @Nullable
        public Item item() {
            return registeredItem;
        }

        public Identifier id() {
            return id;
        }

        public Equipment.Tier tier() {
            return tier;
        }

        public List<AttributeModifier> defaults() {
            return defaults;
        }

        public Supplier<Ingredient> repairIngredientSupplier() {
            return repairIngredientSupplier;
        }

        public RegistryEntry<SoundEvent> equipSound() {
            return equipSound;
        }


//        private static final int durability_t0 = 168;
//        private static final int durability_t1 = 336; // Matches vanilla shield
//        private static final int durability_t2 = 672;
//        private static final int durability_t3 = 1344;
//        private static final int durability_t4 = 4032;


        /**
         * Calculate durability based on tier.
         * Follows 2x progression pattern: 168 -> 336 -> 672 -> 1344 -> 2688
         */
        public int durability() {
            return switch (tier) {
                case WOODEN, GOLDEN -> 168;
                case TIER_0 -> 168;
                case TIER_1 -> 336;  // Vanilla shield
                case TIER_2 -> 672;  // 2x t1
                case TIER_3 -> 1344;
                case TIER_4 -> 2688;
                case TIER_5 -> 4032;
            };
        }

        /**
         * Create the shield item using the provided factory.
         * Factory is passed as a parameter to keep this class independent from fabric-extras.
         *
         * @param settings  Item settings with durability, fireproof, rarity, etc.
         * @param attributes Attribute modifiers to apply
         * @param factory   Shield factory (e.g., CustomShieldItem::new)
         * @return Created shield item
         */
        public Item create(
                Item.Settings settings,
                List<AttributeModifier> attributes,
                ShieldFactory factory
        ) {
            // Convert AttributeModifier list to format expected by shield factory
            ArrayList<Pair<RegistryEntry<EntityAttribute>, EntityAttributeModifier>> shieldAttributes = new ArrayList<>();
            for (var modifier : Weapon.attributesFrom(attributes).modifiers()) {
                shieldAttributes.add(new Pair<>(modifier.attribute(), modifier.modifier()));
            }

            this.registeredItem = factory.create(
                    equipSound,
                    repairIngredientSupplier,
                    shieldAttributes,
                    settings.maxDamage(durability())
            );
            return this.registeredItem;
        }

        // Chainable methods

        public Entry translatedName(String translatedName) {
            this.translatedName = translatedName;
            return this;
        }

        public String translatedName() {
            return translatedName;
        }

        public String translationKey() {
            return Util.createTranslationKey("item", id());
        }

        public Entry rarity(Rarity rarity) {
            this.rarity = rarity;
            return this;
        }

        public Entry spellChoice(SpellChoice choice) {
            this.spellChoice = choice;
            return this;
        }

        public Entry spellContainer(SpellContainer container) {
            this.spellContainer = container;
            return this;
        }

        public Entry withSpellChoices(String pool) {
            this.spellContainer = this.spellContainer.withBindingPool(Identifier.of(pool));
            this.spellChoice = SpellChoice.of(pool);
            return this;
        }

        public Entry lootTheme(String theme) {
            lootProperties = Equipment.LootProperties.of(lootProperties.tier(), theme);
            return this;
        }

        public Entry loot(int tier, String theme) {
            this.lootProperties = Equipment.LootProperties.of(tier, theme);
            return this;
        }
    }

    /**
     * Register shield entries with the provided factory.
     * Factory is passed as a parameter to keep this method independent from fabric-extras.
     *
     * @param configs       Shield configuration map
     * @param entries       List of shield entries to register
     * @param itemGroupKey  Item group to add shields to
     * @param factory       Shield factory (e.g., CustomShieldItem::new)
     */
    public static void register(
            Map<String, ShieldConfig> configs,
            List<Entry> entries,
            RegistryKey<ItemGroup> itemGroupKey,
            ShieldFactory factory
    ) {
        ArrayList<Item> shields = new ArrayList<>();

        for (var entry : entries) {
            // Get or create config
            var config = configs.get(entry.id.toString());
            if (config == null) {
                config = new ShieldConfig();
                config.durability = entry.durability();
                config.attributes = entry.defaults;
                configs.put(entry.id.toString(), config);
            }

            // Create item settings
            var settings = new Item.Settings();
            if (entry.tier().getNumber() >= Equipment.Tier.TIER_3.getNumber()) {
                settings.fireproof();
            }
            if (entry.rarity != Rarity.COMMON) {
                settings.rarity(entry.rarity);
            }

            // Add spell support
            if (entry.spellChoice != null) {
                settings.component(SpellDataComponents.SPELL_CHOICE, entry.spellChoice);
            }
            if (entry.spellContainer != null) {
                settings.component(SpellDataComponents.SPELL_CONTAINER, entry.spellContainer);
            }

            // Create and register item - factory passed here
            var shield = entry.create(settings, config.attributes, factory);
            Registry.register(Registries.ITEM, entry.id, shield);
            entry.registeredItem = shield;
            shields.add(shield);
        }

        // Add to item group
        ItemGroupEvents.modifyEntriesEvent(itemGroupKey).register((content) -> {
            for (var shield : shields) {
                content.add(shield);
            }
        });
    }
}
