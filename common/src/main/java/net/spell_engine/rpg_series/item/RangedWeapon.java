package net.spell_engine.rpg_series.item;

import net.fabric_extras.ranged_weapon.api.RangedConfig;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ToolMaterials;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import net.minecraft.util.Util;
import net.spell_engine.api.spell.SpellDataComponents;
import net.spell_engine.api.spell.container.SpellChoice;
import net.spell_engine.api.spell.container.SpellContainer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class RangedWeapon {

    public interface RangedFactory {
        Item create(Item.Settings settings, RangedConfig config, Supplier<Ingredient> repairIngredientSupplier);
    }

    public static final class Entry {
        private final Identifier id;
        private final RangedFactory factory;
        private final RangedConfig defaults;
        private final Supplier<Ingredient> repairIngredientSupplier;
        private Equipment.Tier tier;

        private String translatedName = "";
        public Rarity rarity = Rarity.COMMON;
        @Nullable private Item registeredItem;

        public String weaponAttributesPreset = "";
        @Nullable public SpellChoice spellChoice;
        @Nullable public SpellContainer spellContainer;

        public Equipment.WeaponType category = Equipment.WeaponType.LONG_BOW;
        public Equipment.LootProperties lootProperties = Equipment.LootProperties.EMPTY;

        public Entry(Identifier id, Equipment.Tier tier, RangedFactory factory, RangedConfig defaults, Supplier<Ingredient> repairIngredientSupplier, Equipment.WeaponType category) {
            this.id = id;
            this.tier = tier;
            this.lootProperties = Equipment.LootProperties.of(tier.getNumber());
            this.factory = factory;
            this.defaults = defaults;
            this.repairIngredientSupplier = repairIngredientSupplier;
            this.category = category;
        }

        @Nullable public Item item() {
            return registeredItem;
        }

        public Identifier id() {
            return id;
        }

        public RangedFactory factory() {
            return factory;
        }

        public RangedConfig defaults() {
            return defaults;
        }

        public Supplier<Ingredient> repairIngredientSupplier() {
            return repairIngredientSupplier;
        }

        public int durability() {
            switch (tier) {
                case WOODEN, GOLDEN -> { return 384; }
                case TIER_0, TIER_1 -> { return 465; }
                case TIER_2 -> { return ToolMaterials.DIAMOND.getDurability(); }
                case TIER_3 -> { return ToolMaterials.NETHERITE.getDurability(); }
                case TIER_4, TIER_5 -> { return ToolMaterials.NETHERITE.getDurability() * 2; }
                default -> { return 250; }
            }
        }

        public Item create(Item.Settings settings, RangedConfig config) {
            this.registeredItem = factory.create(
                    settings.maxDamage(durability()),
                    config,
                    repairIngredientSupplier
            );
            return this.registeredItem;
        }

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
    
    public static void register(Map<String, RangedConfig> rangedConfig, List<Entry> entries, RegistryKey<ItemGroup> itemGroupKey) {
        for (var entry: entries) {
            var config = rangedConfig.get(entry.id.toString());
            if (config == null) {
                config = entry.defaults;
                rangedConfig.put(entry.id.toString(), config);
            }
            var settings = new Item.Settings();
            if (entry.tier.getNumber() >= Equipment.Tier.TIER_3.getNumber()) {
                settings.fireproof();
            }
            if (entry.rarity != Rarity.COMMON) {
                settings.rarity(entry.rarity);
            }
            if (entry.spellChoice != null) {
                settings.component(SpellDataComponents.SPELL_CHOICE, entry.spellChoice);
            }
            if (entry.spellContainer != null) {
                settings.component(SpellDataComponents.SPELL_CONTAINER, entry.spellContainer);
            }
            var item = entry.create(settings, config);
            Registry.register(Registries.ITEM, entry.id, item);
        }
        ItemGroupEvents.modifyEntriesEvent(itemGroupKey).register((content) -> {
            for (var entry: entries) {
                content.add(entry.registeredItem);
            }
        });
    }
}
