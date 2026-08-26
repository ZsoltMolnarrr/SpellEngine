package net.spell_engine.rpg_series.item;

import net.rpg_foundation.ranged_weapon.api.RangedConfig;
import net.spell_engine.PlatformEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Util;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.spell_engine.api.spell.SpellDataComponents;
import net.spell_engine.api.spell.container.SpellChoice;
import net.spell_engine.api.spell.container.SpellContainer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class RangedWeapon {

    public interface RangedFactory {
        Item create(Item.Properties settings, RangedConfig config);
    }

    public static final class Entry {
        private final Identifier id;
        private final RangedFactory factory;
        private final RangedConfig defaults;
        private final @Nullable TagKey<Item> repairItems;
        private Equipment.Tier tier;

        private String translatedName = "";
        public Rarity rarity = Rarity.COMMON;
        @Nullable private Item registeredItem;

        public String weaponAttributesPreset = "";
        @Nullable public SpellChoice spellChoice;
        @Nullable public SpellContainer spellContainer;

        public Equipment.WeaponType category = Equipment.WeaponType.LONG_BOW;
        public Equipment.LootProperties lootProperties = Equipment.LootProperties.EMPTY;

        public Entry(Identifier id, Equipment.Tier tier, RangedFactory factory, RangedConfig defaults, @Nullable TagKey<Item> repairItems, Equipment.WeaponType category) {
            this.id = id;
            this.tier = tier;
            this.lootProperties = Equipment.LootProperties.of(tier.getNumber());
            this.factory = factory;
            this.defaults = defaults;
            this.repairItems = repairItems;
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

        /// Item tag accepted for anvil repair; `null` means the weapon is not repairable.
        public @Nullable TagKey<Item> repairItems() {
            return repairItems;
        }

        public int durability() {
            switch (tier) {
                case WOODEN, GOLDEN -> { return 384; }
                case TIER_0, TIER_1 -> { return 465; }
                case TIER_2 -> { return ToolMaterial.DIAMOND.durability(); }
                case TIER_3 -> { return ToolMaterial.NETHERITE.durability(); }
                case TIER_4, TIER_5 -> { return ToolMaterial.NETHERITE.durability() * 2; }
                default -> { return 250; }
            }
        }

        /// Durability and repair (`minecraft:repairable`) are applied to `settings` here, before the factory runs.
        /// `repairable(TagKey)` requires an unfrozen ITEM registry — always true while items are registered at mod init.
        public Item create(Item.Properties settings, RangedConfig config) {
            settings.durability(durability());
            if (repairItems != null) {
                settings.repairable(repairItems);
            }
            this.registeredItem = factory.create(settings, config);
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
            return Util.makeDescriptionId("item", id());
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
            this.spellContainer = this.spellContainer.withBindingPool(Identifier.parse(pool));
            this.spellChoice = SpellChoice.of(pool);
            return this;
        }

        /// Registers component changes to apply to this item when `spellId` is chosen from the pool.
        /// Lets the chosen spell drive the item's appearance (`custom_model_data`, `custom_name`, ...).
        public Entry applyOnChoice(String spellId, DataComponentPatch changes) {
            if (this.spellChoice == null) {
                this.spellChoice = SpellChoice.EMPTY;
            }
            this.spellChoice = this.spellChoice.withApplyOnChoice(Identifier.parse(spellId), changes);
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
    
    public static void register(Map<String, RangedConfig> rangedConfig, List<Entry> entries, ResourceKey<CreativeModeTab> itemGroupKey) {
        for (var entry: entries) {
            var config = rangedConfig.get(entry.id.toString());
            if (config == null) {
                config = entry.defaults;
                rangedConfig.put(entry.id.toString(), config);
            }
            var settings = new Item.Properties().setId(ResourceKey.create(Registries.ITEM, entry.id()));
            if (entry.tier.getNumber() >= Equipment.Tier.TIER_3.getNumber()) {
                settings.fireResistant();
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
            Registry.register(BuiltInRegistries.ITEM, entry.id, item);
        }
        PlatformEvents.onItemGroupModify(itemGroupKey, (content, context) -> {
            for (var entry: entries) {
                content.accept(entry.registeredItem);
            }
        });
    }
}
