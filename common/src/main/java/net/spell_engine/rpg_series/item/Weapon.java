package net.spell_engine.rpg_series.item;
import net.spell_engine.rpg_series.config.ConfigUtil;
import net.spell_engine.Platform;

import net.spell_engine.PlatformEvents;
import net.minecraft.block.Block;
import net.minecraft.component.ComponentChanges;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.ToolComponent;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ToolMaterial;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import net.spell_engine.rpg_series.config.AttributeModifier;
import net.spell_engine.rpg_series.config.WeaponConfig;
import net.spell_engine.api.spell.SpellDataComponents;
import net.spell_engine.api.spell.container.SpellChoice;
import net.spell_engine.api.spell.container.SpellContainer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class Weapon {

    public interface Factory {
        Item create(ToolMaterial material, Item.Settings settings);
    }

    public static final class Entry {
        private final String namespace;
        private final String name;
        private final CustomMaterial material;
        private final Factory factory;
        @Nullable private Item registeredItem;
        private final WeaponConfig defaults;
        private @Nullable String requiredMod;
        public Rarity rarity = Rarity.COMMON;
        private String translatedName = ""; // Used for data gen

        public String weaponAttributesPreset = ""; // Used for data gen
        @Nullable public SpellChoice spellChoice;
        @Nullable public SpellContainer spellContainer;

        // Loot related classification
        public Equipment.WeaponType category = Equipment.WeaponType.SWORD;
        public Equipment.LootProperties lootProperties = Equipment.LootProperties.EMPTY;

        public Entry(String namespace, String name, CustomMaterial material, Factory factory, WeaponConfig defaults, Equipment.WeaponType category) {
            this.namespace = namespace;
            this.name = name;
            this.material = material;
            this.factory = factory;
            this.defaults = defaults;
            this.category = category;
        }

        public Identifier id() {
            return Identifier.of(namespace, name);
        }

        public Entry attribute(AttributeModifier attribute) {
            defaults.add(attribute);
            return this;
        }

        public Entry requires(String modName) {
            this.requiredMod = modName;
            return this;
        }

        public boolean isRequiredModInstalled() {
            if (requiredMod == null || requiredMod.isEmpty()) {
                return true;
            }
            return Platform.util().isModLoaded(requiredMod);
        }

        public String name() {
            return name;
        }

        public CustomMaterial material() {
            return material;
        }

        public Item create(ToolMaterial material, Item.Settings settings) {
            var item = factory.create(material, settings);
            registeredItem = item;
            return item;
        }

        @Nullable public Item item() {
            return registeredItem;
        }

        public WeaponConfig defaults() {
            return defaults;
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

        public Entry withAdditionalSpell(String spellId) {
            var container = this.spellContainer;
            if (container != null) {
                this.spellContainer = container.withAdditionalSpell(List.of(spellId));
            }
            return this;
        }

        public Entry translatedName(String name) {
            this.translatedName = name;
            return this;
        }

        public String translatedName() {
            return translatedName;
        }

        public Equipment.WeaponType category() {
            return category;
        }

        public Entry loot(Equipment.LootProperties properties) {
            lootProperties = properties;
            return this;
        }

        public Entry lootTheme(String theme) {
            lootProperties = Equipment.LootProperties.of(lootProperties.tier(), theme);
            return this;
        }

        public Equipment.LootProperties lootProperties() {
            return lootProperties;
        }
    }

    // MARK: Material

    /// Wraps a vanilla {@link ToolMaterial} (a record since 1.21.2) with an optional repair item tag.
    /// Repair goes through the vanilla `minecraft:repairable` component, which binds a *live* tag handle:
    /// the tag contents are read at anvil time, so cross-mod items and datapack overrides just work.
    public static class CustomMaterial {
        /// @param repairItems `null` keeps the {@link ToolMaterial}'s own repair tag.
        public static CustomMaterial matching(ToolMaterial vanillaMaterial, @Nullable TagKey<Item> repairItems) {
            return new CustomMaterial(vanillaMaterial, repairItems);
        }

        private final ToolMaterial toolMaterial;
        /// `null` keeps the {@link ToolMaterial}'s own repair tag.
        private final @Nullable TagKey<Item> repairItems;

        public CustomMaterial(ToolMaterial toolMaterial, @Nullable TagKey<Item> repairItems) {
            this.toolMaterial = toolMaterial;
            this.repairItems = repairItems;
        }

        public ToolMaterial toolMaterial() { return toolMaterial; }
        public int getDurability() { return toolMaterial.durability(); }
        public float getMiningSpeedMultiplier() { return toolMaterial.speed(); }
        public int getEnchantability() { return toolMaterial.enchantmentValue(); }
        public TagKey<Block> getInverseTag() { return toolMaterial.incorrectBlocksForDrops(); }
        public @Nullable TagKey<Item> repairItems() { return repairItems; }

        /// Durability, enchantability and repair — no TOOL component.
        ///
        /// `Item.Settings.repairable(TagKey)` looks the tag up through the ITEM registry, which must still be
        /// **unfrozen** — always the case while items are registered at mod init. Never assemble `Item.Settings`
        /// after registry freeze.
        public Item.Settings applyBaseSettings(Item.Settings settings) {
            settings = settings.maxDamage(toolMaterial.durability()).enchantable(toolMaterial.enchantmentValue());
            settings.repairable(repairItems != null ? repairItems : toolMaterial.repairItems());
            return settings;
        }

        /// Base settings plus the vanilla sword TOOL/WEAPON components. Attack attributes must be applied afterwards.
        public Item.Settings applySwordSettings(Item.Settings settings) {
            toolMaterial.applySwordSettings(settings, 0, 0);
            return applyBaseSettings(settings);
        }
    }

    // MARK: Registration

    public static void register(Map<String, WeaponConfig> configs, List<Entry> entries, RegistryKey<ItemGroup> itemGroupKey) {
        for(var entry: entries) {
            var config = configs.get(entry.name);
            if (config == null) {
                config = entry.defaults;
                configs.put(entry.name(), config);
            }
            if (!entry.isRequiredModInstalled()) { continue; }

            var settings = new Item.Settings().registryKey(RegistryKey.of(RegistryKeys.ITEM, entry.id()));
            switch (entry.category) {
                case DAMAGE_STAFF, HEALING_STAFF, DAMAGE_WAND, HEALING_WAND -> entry.material.applyBaseSettings(settings);
                default -> entry.material.applySwordSettings(settings);
            }
            // Attack attributes come from config, overriding whatever the vanilla material set
            settings.attributeModifiers(attributesFrom(config));
            if (entry.rarity != Rarity.COMMON) {
                settings = settings.rarity(entry.rarity);
            }

            if (entry.spellChoice != null) {
                settings.component(SpellDataComponents.SPELL_CHOICE, entry.spellChoice);
            }
            if (entry.spellContainer != null) {
                settings.component(SpellDataComponents.SPELL_CONTAINER, entry.spellContainer);
            }

            var tier = entry.lootProperties().tier();
            if (tier >= 3) {
                settings.fireproof();
            }
            var item = entry.create(entry.material.toolMaterial(), settings);
            Registry.register(Registries.ITEM, entry.id(), item);
        }
        PlatformEvents.onItemGroupModify(itemGroupKey, (content, context) -> {
            for(var entry: entries) {
                content.add(entry.item());
            }
        });
    }

    public static AttributeModifiersComponent attributesFrom(WeaponConfig config) {
        AttributeModifiersComponent.Builder builder = AttributeModifiersComponent.builder();
        builder.add(EntityAttributes.ATTACK_DAMAGE,
                new EntityAttributeModifier(
                        Item.BASE_ATTACK_DAMAGE_MODIFIER_ID,
                        config.attack_damage,
                        EntityAttributeModifier.Operation.ADD_VALUE),
                AttributeModifierSlot.MAINHAND);
        builder.add(EntityAttributes.ATTACK_SPEED,
                new EntityAttributeModifier(
                        Item.BASE_ATTACK_SPEED_MODIFIER_ID,
                        config.attack_speed,
                        EntityAttributeModifier.Operation.ADD_VALUE),
                AttributeModifierSlot.MAINHAND);
        for(var attribute: config.selectedAttributes()) {
            try {
                var entityAttribute = ConfigUtil.attribute(attribute.attribute).orElseThrow();
                builder.add(entityAttribute,
                        new EntityAttributeModifier(
                                equipmentBonusId,
                                attribute.value,
                                attribute.operation),
                        AttributeModifierSlot.MAINHAND);
            } catch (Exception e) {
                System.err.println("Failed to add item attribute modifier: " + e.getMessage());
            }
        }
        return builder.build();
    }

    public static AttributeModifiersComponent attributesFrom(List<AttributeModifier> attributes) {
        AttributeModifiersComponent.Builder builder = AttributeModifiersComponent.builder();
        for(var attribute: attributes) {
            try {
                var entityAttribute = ConfigUtil.attribute(attribute.attribute).orElseThrow();
                builder.add(entityAttribute,
                        new EntityAttributeModifier(
                                equipmentBonusId,
                                attribute.value,
                                attribute.operation),
                        AttributeModifierSlot.MAINHAND);
            } catch (Exception e) {
                System.err.println("Failed to add item attribute modifier: " + e.getMessage());
            }
        }
        return builder.build();
    }

    private static final Identifier equipmentBonusId = Identifier.of("equipment_bonus");
    private static final Identifier projectileDamageId = Identifier.of("projectile_damage", "generic");
}
