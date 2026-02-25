package net.spell_engine.rpg_series.item;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.ToolComponent;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ToolMaterial;
import net.minecraft.item.ToolMaterials;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.Lazy;
import net.minecraft.util.Rarity;
import net.spell_engine.api.config.AttributeModifier;
import net.spell_engine.api.config.WeaponConfig;
import net.spell_engine.api.spell.SpellDataComponents;
import net.spell_engine.api.spell.container.SpellChoice;
import net.spell_engine.api.spell.container.SpellContainer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

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
            return FabricLoader.getInstance().isModLoaded(requiredMod);
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

    public static class CustomMaterial implements ToolMaterial {
        public static CustomMaterial matching(ToolMaterials vanillaMaterial, Supplier<Ingredient> repairIngredient) {
            var material = new CustomMaterial();
            material.durability = vanillaMaterial.getDurability();
            material.miningSpeed = vanillaMaterial.getMiningSpeedMultiplier();
            material.enchantability = vanillaMaterial.getEnchantability();
            material.ingredient = new Lazy(repairIngredient);
            material.inverseTag = vanillaMaterial.getInverseTag();
            return material;
        }

        private TagKey<Block> inverseTag;
        private int durability = 0;
        private float miningSpeed = 0;
        private int enchantability = 0;
        private Lazy<Ingredient> ingredient = null;

        @Override
        public int getDurability() {
            return durability;
        }

        @Override
        public float getMiningSpeedMultiplier() {
            return miningSpeed;
        }

        @Override
        public float getAttackDamage() {
            return 0;
        }

        @Override
        public TagKey<Block> getInverseTag() {
            return inverseTag;
        }

        @Override
        public int getEnchantability() {
            return enchantability;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return (Ingredient)this.ingredient.get();
        }

        @Override
        public ToolComponent createComponent(TagKey<Block> tag) {
            return ToolMaterial.super.createComponent(tag);
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

            var settings = new Item.Settings()
                    .attributeModifiers(attributesFrom(config));
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
            var item = entry.create(entry.material, settings);
            Registry.register(Registries.ITEM, entry.id(), item);
        }
        ItemGroupEvents.modifyEntriesEvent(itemGroupKey).register(content -> {
            for(var entry: entries) {
                content.add(entry.item());
            }
        });
    }

    public static AttributeModifiersComponent attributesFrom(WeaponConfig config) {
        AttributeModifiersComponent.Builder builder = AttributeModifiersComponent.builder();
        builder.add(EntityAttributes.GENERIC_ATTACK_DAMAGE,
                new EntityAttributeModifier(
                        Item.BASE_ATTACK_DAMAGE_MODIFIER_ID,
                        config.attack_damage,
                        EntityAttributeModifier.Operation.ADD_VALUE),
                AttributeModifierSlot.MAINHAND);
        builder.add(EntityAttributes.GENERIC_ATTACK_SPEED,
                new EntityAttributeModifier(
                        Item.BASE_ATTACK_SPEED_MODIFIER_ID,
                        config.attack_speed,
                        EntityAttributeModifier.Operation.ADD_VALUE),
                AttributeModifierSlot.MAINHAND);
        for(var attribute: config.selectedAttributes()) {
            try {
                var attributeId = Identifier.of(attribute.attribute);
                var entityAttribute = Registries.ATTRIBUTE.getEntry(attributeId).get();
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
                var attributeId = Identifier.of(attribute.attribute);
                var entityAttribute = Registries.ATTRIBUTE.getEntry(attributeId).get();
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
    private static final Identifier attackDamageId = Identifier.of("generic.attack_damage");
    private static final Identifier projectileDamageId = Identifier.of("projectile_damage", "generic");
}
