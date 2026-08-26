package net.spell_engine.rpg_series.item;

import net.spell_engine.rpg_series.config.ConfigUtil;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.spell_engine.PlatformEvents;
import net.spell_engine.rpg_series.config.ArmorSetConfig;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Armor {

    /// Armor items are plain items since 1.21.2 (armor behaviour = EQUIPPABLE + attribute components).
    /// Attributes are configurable at registration time, after the item was constructed, so the
    /// component map is rebuilt when {@link #setAttributes} is called.
    public static class CustomItem extends Item implements ConfigurableAttributes {
        public final ArmorMaterial customMaterial;
        public final ArmorType type;
        private DataComponentMap components;

        public CustomItem(ArmorMaterial material, ArmorType type, Properties settings) {
            super(settings.humanoidArmor(material, type));
            this.customMaterial = material;
            this.type = type;
            this.components = super.components();
        }

        @Override
        public void setAttributes(ItemAttributeModifiers attributeModifiers) {
            this.components = DataComponentMap.builder()
                    .addAll(super.components())
                    .set(DataComponents.ATTRIBUTE_MODIFIERS, attributeModifiers)
                    .build();
        }

        @Override
        public DataComponentMap components() {
            return components == null ? super.components() : components;
        }

        public ItemAttributeModifiers getAttributeModifiers() {
            return components().getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        }

        public ArmorType getType() { return type; }
        public EquipmentSlot getSlotType() { return type.getSlot(); }

        /// The equipment asset id (formerly the first armor material layer id)
        public Identifier getFirstLayerId() {
            return customMaterial.assetId().identifier();
        }
    }

    public static class Set<A extends CustomItem> {
        public final String namespace;
        public final String name;
        public final A head, chest, legs, feet;
        public String headTranslation, chestTranslation, legsTranslation, feetTranslation = "";
        public Set(String namespace, String name, A head, A chest, A legs, A feet) {
            this.namespace = namespace;
            this.name = name;
            this.head = head;
            this.chest = chest;
            this.legs = legs;
            this.feet = feet;
        }
        public List<A> pieces() {
            return Stream.of(head, chest, legs, feet).filter(Objects::nonNull).collect(Collectors.toList());
        }

        public Identifier idOf(CustomItem piece) {
            var name = this.name + "_" + piece.getSlotType().getName();
            return Identifier.fromNamespaceAndPath(namespace, name);
        }

        public List<String> idStrings() {
            return pieces().stream().map(piece -> idOf(piece).toString()).toList();
        }
        public List<Identifier> pieceIds() {
            return pieces().stream().map(this::idOf).toList();
        }

        public Set<A> translate(String headName, String chestName, String legsName, String feetName) {
            this.headTranslation = headName;
            this.chestTranslation = chestName;
            this.legsTranslation = legsName;
            this.feetTranslation = feetName;
            return this;
        }

        public void register(ResourceKey<CreativeModeTab> itemGroupKey) {
            for (var piece: pieces()) {
                Registry.register(BuiltInRegistries.ITEM, idOf(piece), piece);
            }
            PlatformEvents.onItemGroupModify(itemGroupKey, (content, context) -> {
                for(var piece: pieces()) {
                    content.accept(piece);
                }
            });
        }

        public interface ItemFactory<T extends CustomItem> {
            T create(ArmorMaterial material, ArmorType slot, Item.Properties settings);
        }
    }

    public record ItemSettingsTweaker(Consumer<Item.Properties> helmet,
                                      Consumer<Item.Properties> chestplate,
                                      Consumer<Item.Properties> leggings,
                                      Consumer<Item.Properties> boots) {
        public static ItemSettingsTweaker standard(Consumer<Item.Properties> consumer) {
            return new ItemSettingsTweaker(consumer, consumer, consumer, consumer);
        }
    }

    public record Entry(ArmorMaterial material, Armor.Set armorSet, ArmorSetConfig defaults, Equipment.LootProperties lootProperties) {
        public static Entry create(ArmorMaterial material, Identifier id, int durability, Set.ItemFactory factory, ArmorSetConfig defaults) {
            return create(material, id, durability, factory, defaults, Equipment.LootProperties.EMPTY);
        }
        public static Entry create(ArmorMaterial material, Identifier id, int durability, Set.ItemFactory factory, ArmorSetConfig defaults, Equipment.LootProperties lootProperties) {
            return create(material, id, durability, factory, defaults, lootProperties, null);
        }
        public static Entry create(ArmorMaterial material, Identifier id, int durability, Set.ItemFactory factory, ArmorSetConfig defaults,
                                   Equipment.LootProperties lootProperties, @Nullable ItemSettingsTweaker settingsTweaker) {

            var helmetSettings = new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, id.withSuffix("_" + ArmorType.HELMET.getSlot().getName())))
                    .durability(ArmorType.HELMET.getDurability(durability));
            var chestplateSettings = new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, id.withSuffix("_" + ArmorType.CHESTPLATE.getSlot().getName())))
                    .durability(ArmorType.CHESTPLATE.getDurability(durability));
            var leggingsSettings = new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, id.withSuffix("_" + ArmorType.LEGGINGS.getSlot().getName())))
                    .durability(ArmorType.LEGGINGS.getDurability(durability));
            var bootsSettings = new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, id.withSuffix("_" + ArmorType.BOOTS.getSlot().getName())))
                    .durability(ArmorType.BOOTS.getDurability(durability));
            if (settingsTweaker != null) {
                settingsTweaker.helmet.accept(helmetSettings);
                settingsTweaker.chestplate.accept(chestplateSettings);
                settingsTweaker.leggings.accept(leggingsSettings);
                settingsTweaker.boots.accept(bootsSettings);
            }

            var tier = lootProperties.tier();
            if (tier >= 3) {
                helmetSettings.fireResistant();
                chestplateSettings.fireResistant();
                leggingsSettings.fireResistant();
                bootsSettings.fireResistant();
            }

            var set = new Armor.Set(id.getNamespace(), id.getPath(),
                    factory.create(material, ArmorType.HELMET, helmetSettings),
                    factory.create(material, ArmorType.CHESTPLATE, chestplateSettings),
                    factory.create(material, ArmorType.LEGGINGS, leggingsSettings),
                    factory.create(material, ArmorType.BOOTS, bootsSettings)
            );
            return new Entry(material, set, defaults, lootProperties);
        }


        public Entry translatedName(String headName, String chestName, String legsName, String feetName) {
            armorSet.translate(headName, chestName, legsName, feetName);
            return this;
        }

        public String name() {
            return armorSet.name;
        }

        public <T extends CustomItem> Entry bundle(Function<ArmorMaterial, Armor.Set<T>> factory) {
            var armorSet = factory.apply(material);
            return new Entry(material, armorSet, defaults, lootProperties);
        }

        public <T extends CustomItem> Entry put(ArrayList<Entry> list) {
            list.add(this);
            return this;
        }
    }

    // MARK: Registration

    public static void register(Map<String, ArmorSetConfig> configs, List<Entry> entries, ResourceKey<CreativeModeTab> itemGroupKey) {
        for(var entry: entries) {
            var config = configs.get(entry.name());
            if (config == null) {
                config = entry.defaults();
                configs.put(entry.name(), config);
            }
            for (var piece: entry.armorSet().pieces()) {
                ((ConfigurableAttributes)piece).setAttributes(attributesFrom(config, ((CustomItem) piece).getType()));
            }
            entry.armorSet().register(itemGroupKey);
        }
    }

    private static ItemAttributeModifiers attributesFrom(ArmorSetConfig config, ArmorType slot) {
        ArmorSetConfig.Piece piece = null;
        var modifierId = Identifier.withDefaultNamespace("armor." + slot.getName());
        switch (slot) {
            case ArmorType.BOOTS -> {
                piece = config.feet;
            }
            case ArmorType.LEGGINGS -> {
                piece = config.legs;
            }
            case ArmorType.CHESTPLATE -> {
                piece = config.chest;
            }
            case ArmorType.HELMET -> {
                piece = config.head;
            }
        }

        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
        EquipmentSlotGroup attributeModifierSlot = EquipmentSlotGroup.bySlot(slot.getSlot());

        if (config.armor_toughness != 0) {

            builder.add(Attributes.ARMOR_TOUGHNESS,
                    new AttributeModifier(
                            modifierId,
                            config.armor_toughness,
                            AttributeModifier.Operation.ADD_VALUE),
                    attributeModifierSlot);
        }
        if (config.knockback_resistance != 0) {
            builder.add(Attributes.KNOCKBACK_RESISTANCE,
                    new AttributeModifier(
                            modifierId,
                            config.knockback_resistance,
                            AttributeModifier.Operation.ADD_VALUE),
                    attributeModifierSlot);
        }
        if (piece.armor != 0) {
            builder.add(Attributes.ARMOR,
                    new AttributeModifier(
                            modifierId,
                            piece.armor,
                            AttributeModifier.Operation.ADD_VALUE),
                    attributeModifierSlot);
        }
        for (var attribute: piece.selectedAttributes()) {
            try {
                var entityAttribute = ConfigUtil.attribute(attribute.attribute).orElseThrow();
                builder.add(entityAttribute,
                        new AttributeModifier(
                                modifierId,
                                attribute.value,
                                attribute.operation),
                        attributeModifierSlot);
            } catch (Exception e) {
                System.err.println("Failed to add item attribute modifier: " + e.getMessage());
            }
        }

        return builder.build();
    }
}
