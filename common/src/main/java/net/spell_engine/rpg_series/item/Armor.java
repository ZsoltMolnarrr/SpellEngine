package net.spell_engine.rpg_series.item;

import net.spell_engine.PlatformEvents;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.equipment.ArmorMaterial;
import net.minecraft.item.equipment.EquipmentType;
import net.minecraft.item.ItemGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
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
        public final EquipmentType type;
        private ComponentMap components;

        public CustomItem(ArmorMaterial material, EquipmentType type, Settings settings) {
            super(settings.armor(material, type));
            this.customMaterial = material;
            this.type = type;
            this.components = super.getComponents();
        }

        @Override
        public void setAttributes(AttributeModifiersComponent attributeModifiers) {
            this.components = ComponentMap.builder()
                    .addAll(super.getComponents())
                    .add(DataComponentTypes.ATTRIBUTE_MODIFIERS, attributeModifiers)
                    .build();
        }

        @Override
        public ComponentMap getComponents() {
            return components == null ? super.getComponents() : components;
        }

        public AttributeModifiersComponent getAttributeModifiers() {
            return getComponents().getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT);
        }

        public EquipmentType getType() { return type; }
        public EquipmentSlot getSlotType() { return type.getEquipmentSlot(); }

        /// The equipment asset id (formerly the first armor material layer id)
        public Identifier getFirstLayerId() {
            return customMaterial.assetId().getValue();
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
            return Identifier.of(namespace, name);
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

        public void register(RegistryKey<ItemGroup> itemGroupKey) {
            for (var piece: pieces()) {
                Registry.register(Registries.ITEM, idOf(piece), piece);
            }
            PlatformEvents.onItemGroupModify(itemGroupKey, (content, context) -> {
                for(var piece: pieces()) {
                    content.add(piece);
                }
            });
        }

        public interface ItemFactory<T extends CustomItem> {
            T create(ArmorMaterial material, EquipmentType slot, Item.Settings settings);
        }
    }

    public record ItemSettingsTweaker(Consumer<Item.Settings> helmet,
                                      Consumer<Item.Settings> chestplate,
                                      Consumer<Item.Settings> leggings,
                                      Consumer<Item.Settings> boots) {
        public static ItemSettingsTweaker standard(Consumer<Item.Settings> consumer) {
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

            var helmetSettings = new Item.Settings()
                    .maxDamage(EquipmentType.HELMET.getMaxDamage(durability));
            var chestplateSettings = new Item.Settings()
                    .maxDamage(EquipmentType.CHESTPLATE.getMaxDamage(durability));
            var leggingsSettings = new Item.Settings()
                    .maxDamage(EquipmentType.LEGGINGS.getMaxDamage(durability));
            var bootsSettings = new Item.Settings()
                    .maxDamage(EquipmentType.BOOTS.getMaxDamage(durability));
            if (settingsTweaker != null) {
                settingsTweaker.helmet.accept(helmetSettings);
                settingsTweaker.chestplate.accept(chestplateSettings);
                settingsTweaker.leggings.accept(leggingsSettings);
                settingsTweaker.boots.accept(bootsSettings);
            }

            var tier = lootProperties.tier();
            if (tier >= 3) {
                helmetSettings.fireproof();
                chestplateSettings.fireproof();
                leggingsSettings.fireproof();
                bootsSettings.fireproof();
            }

            var set = new Armor.Set(id.getNamespace(), id.getPath(),
                    factory.create(material, EquipmentType.HELMET, helmetSettings),
                    factory.create(material, EquipmentType.CHESTPLATE, chestplateSettings),
                    factory.create(material, EquipmentType.LEGGINGS, leggingsSettings),
                    factory.create(material, EquipmentType.BOOTS, bootsSettings)
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

    public static void register(Map<String, ArmorSetConfig> configs, List<Entry> entries, RegistryKey<ItemGroup> itemGroupKey) {
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

    private static AttributeModifiersComponent attributesFrom(ArmorSetConfig config, EquipmentType slot) {
        ArmorSetConfig.Piece piece = null;
        var modifierId = Identifier.ofVanilla("armor." + slot.getName());
        switch (slot) {
            case EquipmentType.BOOTS -> {
                piece = config.feet;
            }
            case EquipmentType.LEGGINGS -> {
                piece = config.legs;
            }
            case EquipmentType.CHESTPLATE -> {
                piece = config.chest;
            }
            case EquipmentType.HELMET -> {
                piece = config.head;
            }
        }

        AttributeModifiersComponent.Builder builder = AttributeModifiersComponent.builder();
        AttributeModifierSlot attributeModifierSlot = AttributeModifierSlot.forEquipmentSlot(slot.getEquipmentSlot());

        if (config.armor_toughness != 0) {

            builder.add(EntityAttributes.ARMOR_TOUGHNESS,
                    new EntityAttributeModifier(
                            modifierId,
                            config.armor_toughness,
                            EntityAttributeModifier.Operation.ADD_VALUE),
                    attributeModifierSlot);
        }
        if (config.knockback_resistance != 0) {
            builder.add(EntityAttributes.KNOCKBACK_RESISTANCE,
                    new EntityAttributeModifier(
                            modifierId,
                            config.knockback_resistance,
                            EntityAttributeModifier.Operation.ADD_VALUE),
                    attributeModifierSlot);
        }
        if (piece.armor != 0) {
            builder.add(EntityAttributes.ARMOR,
                    new EntityAttributeModifier(
                            modifierId,
                            piece.armor,
                            EntityAttributeModifier.Operation.ADD_VALUE),
                    attributeModifierSlot);
        }
        for (var attribute: piece.selectedAttributes()) {
            try {
                var entityAttribute = Registries.ATTRIBUTE.getEntry(Identifier.of(attribute.attribute)).orElseThrow();
                builder.add(entityAttribute,
                        new EntityAttributeModifier(
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
