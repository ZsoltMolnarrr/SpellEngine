package net.spell_engine.rpg_series.item;

import net.spell_engine.PlatformEvents;
import net.minecraft.component.ComponentChanges;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.BlocksAttacksComponent;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.minecraft.util.Rarity;
import net.minecraft.util.Util;
import net.spell_engine.rpg_series.config.AttributeModifier;
import net.spell_engine.rpg_series.config.ShieldConfig;
import net.spell_engine.api.spell.SpellDataComponents;
import net.spell_engine.api.spell.container.SpellChoice;
import net.spell_engine.api.spell.container.SpellContainer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/// Shield entries + registration. Shields are built purely from vanilla data components (no library, no
/// `Item` subclass) — see {@link #createVanilla} / {@link #DEFAULT_FACTORY}:
///
/// - **blocking** — `minecraft:blocks_attacks` ({@link #VANILLA_SHIELD_BLOCKING}, the values of `Items.SHIELD`)
/// - **off-hand slot + equip sound** — `minecraft:equippable` (unswappable, like the vanilla shield)
/// - **break sound** — `minecraft:break_sound`
/// - **durability** — `minecraft:max_damage` (from {@link Entry#durability()})
/// - **repair** — `minecraft:repairable` snapshot + the lazily resolved {@link LazyRepair} component
/// - **attributes** — `minecraft:attribute_modifiers` (`HAND` slot), baked at construction from the shield config
///   (configs are loaded before item registration, so no post-construction mutation is needed)
/// - **blocking model** — consumer asset `assets/<ns>/items/<shield>.json`, a `minecraft:condition` on
///   `minecraft:using_item` (the 1.21.4 replacement for the removed `blocking` model predicate)
public class Shield {

    /// Produces the shield `Item` from the assembled settings. The default is {@link #DEFAULT_FACTORY}
    /// ({@link #createVanilla}); override only for a custom `Item` subclass. `settings` already carries
    /// durability, rarity, fireproof, spell components and the repair components when the factory is called;
    /// `repairIngredient` is passed for information only.
    public interface ShieldFactory {
        Item create(
                @Nullable RegistryEntry<SoundEvent> equipSound,
                Supplier<Ingredient> repairIngredient,
                List<Pair<RegistryEntry<EntityAttribute>, EntityAttributeModifier>> attributes,
                Item.Settings settings
        );
    }

    /// Vanilla shield blocking: 0.25 s delay, 90 degree cone, full reduction,
    /// 3+ damage consumes durability, axes disable it, vanilla block/break sounds.
    public static final BlocksAttacksComponent VANILLA_SHIELD_BLOCKING = new BlocksAttacksComponent(
            0.25F,
            1.0F,
            List.of(new BlocksAttacksComponent.DamageReduction(90.0F, Optional.empty(), 0.0F, 1.0F)),
            new BlocksAttacksComponent.ItemDamage(3.0F, 1.0F, 1.0F),
            Optional.of(DamageTypeTags.BYPASSES_SHIELD),
            Optional.of(SoundEvents.ITEM_SHIELD_BLOCK),
            Optional.of(SoundEvents.ITEM_SHIELD_BREAK)
    );

    /// The built-in {@link ShieldFactory}: a plain `Item` assembled from vanilla components.
    public static final ShieldFactory DEFAULT_FACTORY = Shield::createVanilla;

    /// {@link ShieldFactory} implementation producing a plain `new Item(settings)` with the vanilla shield
    /// components applied. Repair components are expected to be on `settings` already (see {@link Entry#create}).
    public static Item createVanilla(
            @Nullable RegistryEntry<SoundEvent> equipSound,
            Supplier<Ingredient> repairIngredient,
            List<Pair<RegistryEntry<EntityAttribute>, EntityAttributeModifier>> attributes,
            Item.Settings settings
    ) {
        return new Item(applyVanillaComponents(settings, equipSound, attributes));
    }

    /// Applies `blocks_attacks`, `equippable` (offhand, unswappable, equip sound), `break_sound` and
    /// `attribute_modifiers` (`HAND` slot) to `settings`. Useful for custom factories that want the vanilla
    /// shield behaviour on their own `Item` subclass.
    public static Item.Settings applyVanillaComponents(
            Item.Settings settings,
            @Nullable RegistryEntry<SoundEvent> equipSound,
            List<Pair<RegistryEntry<EntityAttribute>, EntityAttributeModifier>> attributes
    ) {
        var equippable = EquippableComponent.builder(EquipmentSlot.OFFHAND).swappable(false);
        if (equipSound != null) {
            equippable.equipSound(equipSound);
        }
        return settings.component(DataComponentTypes.BLOCKS_ATTACKS, VANILLA_SHIELD_BLOCKING)
                .component(DataComponentTypes.EQUIPPABLE, equippable.build())
                .component(DataComponentTypes.BREAK_SOUND, SoundEvents.ITEM_SHIELD_BREAK)
                .attributeModifiers(handAttributes(attributes));
    }

    public static AttributeModifiersComponent handAttributes(
            List<Pair<RegistryEntry<EntityAttribute>, EntityAttributeModifier>> attributes) {
        var builder = AttributeModifiersComponent.builder();
        for (var pair : attributes) {
            builder.add(pair.getLeft(), pair.getRight(), AttributeModifierSlot.HAND);
        }
        return builder.build();
    }

    /// Shield entry: id, tier, default attributes, lazy repair ingredient, equip sound, loot/spell metadata.
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

        /// Creates the shield item with the built-in vanilla-component factory.
        public Item create(Item.Settings settings, List<AttributeModifier> attributes) {
            return create(settings, attributes, DEFAULT_FACTORY);
        }

        /// Creates the shield item using `factory`. Durability and repair (`minecraft:repairable` snapshot +
        /// {@link LazyRepair}) are applied to `settings` here, before the factory runs, so every factory gets them.
        ///
        /// @param settings   Item settings with fireproof, rarity, spell components etc.
        /// @param attributes Attribute modifiers to apply (attribute ids as registry ids, e.g. `minecraft:armor_toughness`)
        /// @param factory    Shield factory ({@link #DEFAULT_FACTORY} or a custom one)
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

            settings.maxDamage(durability());
            LazyRepair.apply(settings, repairIngredientSupplier);

            this.registeredItem = factory.create(
                    equipSound,
                    repairIngredientSupplier,
                    shieldAttributes,
                    settings
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

        /// Registers component changes to apply to this item when `spellId` is chosen from the pool.
        /// Lets the chosen spell drive the item's appearance (`custom_model_data`, `custom_name`, ...).
        public Entry applyOnChoice(String spellId, ComponentChanges changes) {
            if (this.spellChoice == null) {
                this.spellChoice = SpellChoice.EMPTY;
            }
            this.spellChoice = this.spellChoice.withApplyOnChoice(Identifier.of(spellId), changes);
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

    /// Registers shield entries with the built-in vanilla-component factory ({@link #DEFAULT_FACTORY}).
    public static void register(
            Map<String, ShieldConfig> configs,
            List<Entry> entries,
            RegistryKey<ItemGroup> itemGroupKey
    ) {
        register(configs, entries, itemGroupKey, DEFAULT_FACTORY);
    }

    /// Registers shield entries with a custom factory.
    ///
    /// @param configs       Shield configuration map (loaded before this call; missing entries are filled from defaults)
    /// @param entries       List of shield entries to register
    /// @param itemGroupKey  Item group to add shields to
    /// @param factory       Shield factory ({@link #DEFAULT_FACTORY} or a custom `Item` subclass factory)
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
            var settings = new Item.Settings().registryKey(RegistryKey.of(RegistryKeys.ITEM, entry.id()));
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

            // Create and register item
            var shield = entry.create(settings, config.selectedAttributes(), factory);
            Registry.register(Registries.ITEM, entry.id, shield);
            entry.registeredItem = shield;
            shields.add(shield);
        }

        // Add to item group
        PlatformEvents.onItemGroupModify(itemGroupKey, (content, context) -> {
            for (var shield : shields) {
                content.add(shield);
            }
        });
    }
}
