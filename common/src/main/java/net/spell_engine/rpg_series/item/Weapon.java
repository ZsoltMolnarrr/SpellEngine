package net.spell_engine.rpg_series.item;
import net.spell_engine.Platform;

import net.spell_engine.PlatformEvents;
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
import net.minecraft.util.Identifier;
import net.minecraft.util.Lazy;
import net.minecraft.util.Rarity;
import net.spell_engine.api.item.ItemAttributeModifiers;
import net.spell_engine.api.item.SpellItemData;
import net.spell_engine.rpg_series.config.AttributeModifier;
import net.spell_engine.rpg_series.config.WeaponConfig;
import net.spell_engine.api.spell.container.SpellChoice;
import net.spell_engine.api.spell.container.SpellContainer;
import net.spell_engine.utils.AttributeModifierUtil;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
            return new Identifier(namespace, name);
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
            this.spellContainer = this.spellContainer.withBindingPool(new Identifier(pool));
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
            material.miningLevel = vanillaMaterial.getMiningLevel();
            return material;
        }

        private int miningLevel = 0;
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
        public int getMiningLevel() {
            return miningLevel;
        }

        @Override
        public int getEnchantability() {
            return enchantability;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return (Ingredient)this.ingredient.get();
        }
    }

    // MARK: Registration

    public static void register(Map<String, WeaponConfig> configs, List<Entry> entries, RegistryKey<ItemGroup> itemGroupKey) {
        itemsToRegister(configs, entries, itemGroupKey)
                .forEach((id, item) -> Registry.register(Registries.ITEM, id, item));
    }

    /// Creates and configures every weapon item of `entries` and returns them keyed by the id they register
    /// under; also installs the item-group contents callback. Creation only — nothing is written into the
    /// ITEM registry here, so a loader that registers items itself (Forge) iterates this instead of calling
    /// {@link #register}. **Must run inside the ITEM registration window** (item constructors create
    /// intrusive registry holders).
    public static Map<Identifier, Item> itemsToRegister(Map<String, WeaponConfig> configs, List<Entry> entries, RegistryKey<ItemGroup> itemGroupKey) {
        var items = new LinkedHashMap<Identifier, Item>();
        for(var entry: entries) {
            var config = configs.get(entry.name);
            if (config == null) {
                config = entry.defaults;
                configs.put(entry.name(), config);
            }
            if (!entry.isRequiredModInstalled()) { continue; }

            var settings = new Item.Settings();
            if (entry.rarity != Rarity.COMMON) {
                settings = settings.rarity(entry.rarity);
            }

            var tier = entry.lootProperties().tier();
            if (tier >= 3) {
                settings.fireproof();
            }
            var item = entry.create(entry.material, settings);
            // Item-level defaults (1.20.1 stand-in for `Item.Settings#component` / `#attributeModifiers`)
            AttributeModifierUtil.setItemModifiers(item, attributesFrom(config));
            if (entry.spellChoice != null || entry.spellContainer != null) {
                SpellItemData.defaults(item)
                        .spellChoice(entry.spellChoice)
                        .spellContainer(entry.spellContainer);
            }
            items.put(entry.id(), item);
        }
        PlatformEvents.onItemGroupModify(itemGroupKey, (content, context) -> {
            for(var entry: entries) {
                content.add(entry.item());
            }
        });
        return items;
    }

    public static ItemAttributeModifiers attributesFrom(WeaponConfig config) {
        var builder = ItemAttributeModifiers.builder();
        builder.add(EntityAttributes.GENERIC_ATTACK_DAMAGE,
                new EntityAttributeModifier(
                        ItemAccessor.ATTACK_DAMAGE_MODIFIER_ID(),
                        "Weapon modifier",
                        config.attack_damage,
                        EntityAttributeModifier.Operation.ADDITION),
                ItemAttributeModifiers.Slot.MAINHAND);
        builder.add(EntityAttributes.GENERIC_ATTACK_SPEED,
                new EntityAttributeModifier(
                        ItemAccessor.ATTACK_SPEED_MODIFIER_ID(),
                        "Weapon modifier",
                        config.attack_speed,
                        EntityAttributeModifier.Operation.ADDITION),
                ItemAttributeModifiers.Slot.MAINHAND);
        builder.addAll(attributesFrom(config.selectedAttributes()));
        return builder.build();
    }

    public static ItemAttributeModifiers attributesFrom(List<AttributeModifier> attributes) {
        var builder = ItemAttributeModifiers.builder();
        for(var attribute: attributes) {
            try {
                var attributeId = new Identifier(attribute.attribute);
                var entityAttribute = AttributeModifierUtil.attributeEntry(attributeId)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown attribute: " + attributeId));
                builder.add(entityAttribute,
                        modifierFor(attributeId, attribute.value, attribute.operation),
                        ItemAttributeModifiers.Slot.MAINHAND);
            } catch (Exception e) {
                System.err.println("Failed to add item attribute modifier: " + e.getMessage());
            }
        }
        return builder.build();
    }

    /// Extra (non-base) weapon modifiers share the `equipment_bonus` id (UUID derived from it).
    /// Projectile damage is keyed like vanilla attack damage, so ranged-weapon tooltips show it as the base value.
    private static EntityAttributeModifier modifierFor(Identifier attributeId, double value, EntityAttributeModifier.Operation operation) {
        if (attributeId.equals(projectileDamageId) && operation == EntityAttributeModifier.Operation.ADDITION) {
            return new EntityAttributeModifier(ItemAccessor.ATTACK_DAMAGE_MODIFIER_ID(), "Weapon modifier", value, operation);
        }
        return AttributeModifierUtil.modifier(equipmentBonusId, value, operation);
    }

    /// Vanilla's base attack damage / speed modifier UUIDs are `protected` on `Item` in 1.20.1
    private static abstract class ItemAccessor extends Item {
        public ItemAccessor(Settings settings) { super(settings); }
        public static UUID ATTACK_DAMAGE_MODIFIER_ID() { return ATTACK_DAMAGE_MODIFIER_ID; }
        public static UUID ATTACK_SPEED_MODIFIER_ID() { return ATTACK_SPEED_MODIFIER_ID; }
    }

    public static UUID baseAttackDamageModifierId() { return ItemAccessor.ATTACK_DAMAGE_MODIFIER_ID(); }
    public static UUID baseAttackSpeedModifierId() { return ItemAccessor.ATTACK_SPEED_MODIFIER_ID(); }

    public static final Identifier equipmentBonusId = new Identifier("equipment_bonus");
    private static final Identifier attackDamageId = new Identifier("generic.attack_damage");
    private static final Identifier projectileDamageId = new Identifier("projectile_damage", "generic");
}
