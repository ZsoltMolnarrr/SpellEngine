package net.spell_engine.rpg_series.item;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.spell_engine.api.config.ArmorSetConfig;
import net.spell_engine.mixin.item.ArmorMaterialLayerAccessor;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Armor {

    public static class CustomItem extends ArmorItem implements ConfigurableAttributes {
        private AttributeModifiersComponent attributeModifiers = AttributeModifiersComponent.builder().build();
        public final RegistryEntry<ArmorMaterial> customMaterial;

        public CustomItem(RegistryEntry<ArmorMaterial> material, Type slot, Settings settings) {
            super(material, slot, settings);
            this.customMaterial = material;
        }

        @Override
        public void setAttributes(AttributeModifiersComponent attributeModifiers) {
            this.attributeModifiers = attributeModifiers;
        }

        @Override
        public AttributeModifiersComponent getAttributeModifiers() {
            return this.attributeModifiers;
        }

        public Identifier getFirstLayerId() {
            var fristLayer = customMaterial.value().layers().getFirst();
            return ((ArmorMaterialLayerAccessor) (Object)fristLayer).spellEngine_getId();
        }
    }

    public static class Set<A extends ArmorItem> {
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

        public Identifier idOf(ArmorItem piece) {
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
            ItemGroupEvents.modifyEntriesEvent(itemGroupKey).register(content -> {
                for(var piece: pieces()) {
                    content.add(piece);
                }
            });
        }

        public interface ItemFactory<T extends ArmorItem> {
            T create(RegistryEntry<ArmorMaterial> material, ArmorItem.Type slot, Item.Settings settings);
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

    public record Entry(RegistryEntry<ArmorMaterial> material, Armor.Set armorSet, ArmorSetConfig defaults, Equipment.LootProperties lootProperties) {
        public static Entry create(RegistryEntry<ArmorMaterial> material, Identifier id, int durability, Set.ItemFactory factory, ArmorSetConfig defaults) {
            return create(material, id, durability, factory, defaults, Equipment.LootProperties.EMPTY);
        }
        public static Entry create(RegistryEntry<ArmorMaterial> material, Identifier id, int durability, Set.ItemFactory factory, ArmorSetConfig defaults, Equipment.LootProperties lootProperties) {
            return create(material, id, durability, factory, defaults, lootProperties, null);
        }
        public static Entry create(RegistryEntry<ArmorMaterial> material, Identifier id, int durability, Set.ItemFactory factory, ArmorSetConfig defaults,
                                   Equipment.LootProperties lootProperties, @Nullable ItemSettingsTweaker settingsTweaker) {

            var helmetSettings = new Item.Settings()
                    .maxDamage(ArmorItem.Type.HELMET.getMaxDamage(durability));
            var chestplateSettings = new Item.Settings()
                    .maxDamage(ArmorItem.Type.CHESTPLATE.getMaxDamage(durability));
            var leggingsSettings = new Item.Settings()
                    .maxDamage(ArmorItem.Type.LEGGINGS.getMaxDamage(durability));
            var bootsSettings = new Item.Settings()
                    .maxDamage(ArmorItem.Type.BOOTS.getMaxDamage(durability));
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
                    factory.create(material, ArmorItem.Type.HELMET, helmetSettings),
                    factory.create(material, ArmorItem.Type.CHESTPLATE, chestplateSettings),
                    factory.create(material, ArmorItem.Type.LEGGINGS, leggingsSettings),
                    factory.create(material, ArmorItem.Type.BOOTS, bootsSettings)
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

        public <T extends ArmorItem> Entry bundle(Function<RegistryEntry<ArmorMaterial>, Armor.Set<T>> factory) {
            var armorSet = factory.apply(material);
            return new Entry(material, armorSet, defaults, lootProperties);
        }

        public <T extends ArmorItem> Entry put(ArrayList<Entry> list) {
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
                var slot = ((ArmorItem)piece).getSlotType();
                ((ConfigurableAttributes)piece).setAttributes(attributesFrom(config, ((ArmorItem) piece).getType()));
            }
            entry.armorSet().register(itemGroupKey);
        }
    }

    private static AttributeModifiersComponent attributesFrom(ArmorSetConfig config, ArmorItem.Type slot) {
        ArmorSetConfig.Piece piece = null;
        var modifierId = Identifier.ofVanilla("armor." + slot.getName());
        switch (slot) {
            case ArmorItem.Type.BOOTS -> {
                piece = config.feet;
            }
            case ArmorItem.Type.LEGGINGS -> {
                piece = config.legs;
            }
            case ArmorItem.Type.CHESTPLATE -> {
                piece = config.chest;
            }
            case ArmorItem.Type.HELMET -> {
                piece = config.head;
            }
        }

        AttributeModifiersComponent.Builder builder = AttributeModifiersComponent.builder();
        AttributeModifierSlot attributeModifierSlot = AttributeModifierSlot.forEquipmentSlot(slot.getEquipmentSlot());

        if (config.armor_toughness != 0) {

            builder.add(EntityAttributes.GENERIC_ARMOR_TOUGHNESS,
                    new EntityAttributeModifier(
                            modifierId,
                            config.armor_toughness,
                            EntityAttributeModifier.Operation.ADD_VALUE),
                    attributeModifierSlot);
        }
        if (config.knockback_resistance != 0) {
            builder.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE,
                    new EntityAttributeModifier(
                            modifierId,
                            config.knockback_resistance,
                            EntityAttributeModifier.Operation.ADD_VALUE),
                    attributeModifierSlot);
        }
        if (piece.armor != 0) {
            builder.add(EntityAttributes.GENERIC_ARMOR,
                    new EntityAttributeModifier(
                            modifierId,
                            piece.armor,
                            EntityAttributeModifier.Operation.ADD_VALUE),
                    attributeModifierSlot);
        }
        for (var attribute: piece.selectedAttributes()) {
            try {
                var entityAttribute = Registries.ATTRIBUTE.getEntry(Identifier.of(attribute.attribute)).get();
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
