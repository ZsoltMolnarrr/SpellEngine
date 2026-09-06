package net.spell_engine.rpg_series.item;

import com.google.common.collect.Multimap;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.Lazy;
import net.spell_engine.Platform;
import net.spell_engine.PlatformEvents;
import net.spell_engine.api.item.ItemAttributeModifiers;
import net.spell_engine.rpg_series.config.ArmorSetConfig;
import net.spell_engine.utils.AttributeModifierUtil;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Armor {

    // MARK: Material

    /// 1.20.1 armor materials are plain `ArmorMaterial` implementations (no registry, no layers).
    /// The texture identity of the set is `id` (`<namespace>:<name>`, mirroring the 1.21 `ArmorMaterial.Layer` id):
    /// `textures/models/armor/<name>_layer_{1,2}.png`.
    public static class CustomMaterial implements ArmorMaterial {
        private static final EnumMap<ArmorItem.Type, Integer> BASE_DURABILITY = new EnumMap<>(Map.of(
                ArmorItem.Type.BOOTS, 13,
                ArmorItem.Type.LEGGINGS, 15,
                ArmorItem.Type.CHESTPLATE, 16,
                ArmorItem.Type.HELMET, 11
        ));

        public final Identifier id;
        private final int durabilityMultiplier;
        private final int enchantability;
        private final Supplier<SoundEvent> equipSound;
        private final Lazy<Ingredient> repairIngredient;
        private final EnumMap<ArmorItem.Type, Integer> protection;
        private final float toughness;
        private final float knockbackResistance;

        public CustomMaterial(Identifier id, Map<ArmorItem.Type, Integer> protection, int enchantability,
                              Supplier<SoundEvent> equipSound, Supplier<Ingredient> repairIngredient,
                              float toughness, float knockbackResistance, int durabilityMultiplier) {
            this.id = id;
            this.protection = new EnumMap<>(ArmorItem.Type.class);
            for (var type : ArmorItem.Type.values()) {
                this.protection.put(type, protection.getOrDefault(type, 0));
            }
            this.enchantability = enchantability;
            this.equipSound = equipSound;
            this.repairIngredient = new Lazy<>(repairIngredient);
            this.toughness = toughness;
            this.knockbackResistance = knockbackResistance;
            this.durabilityMultiplier = durabilityMultiplier;
        }

        @Override
        public int getDurability(ArmorItem.Type type) {
            return BASE_DURABILITY.get(type) * durabilityMultiplier;
        }

        @Override
        public int getProtection(ArmorItem.Type type) {
            return protection.get(type);
        }

        @Override
        public int getEnchantability() {
            return enchantability;
        }

        @Override
        public SoundEvent getEquipSound() {
            return equipSound.get();
        }

        @Override
        public Ingredient getRepairIngredient() {
            return repairIngredient.get();
        }

        /// Texture name. Forge resolves a namespaced name natively (`<ns>:textures/models/armor/<name>_layer_1.png`);
        /// vanilla/Fabric builds `minecraft:textures/models/armor/<getName()>_layer_1.png` and would reject a `:`
        /// in it, so the plain path is returned there (a client mixin can route it to the namespace, see port notes).
        @Override
        public String getName() {
            return Platform.Forge ? id.toString() : id.getPath();
        }

        @Override
        public float getToughness() {
            return toughness;
        }

        @Override
        public float getKnockbackResistance() {
            return knockbackResistance;
        }
    }

    /// Creates an armor material. Mirrors the 1.21 `new ArmorMaterial(protection, enchantability, equipSound, repairIngredient, layers, toughness, knockbackResistance)`
    /// + `Registry.registerReference(Registries.ARMOR_MATERIAL, id, material)` idiom content mods used: `id` doubles as the (single) layer id.
    public static CustomMaterial material(Identifier id, Map<ArmorItem.Type, Integer> protection, int enchantability,
                                          RegistryEntry<SoundEvent> equipSound, Supplier<Ingredient> repairIngredient,
                                          float toughness, float knockbackResistance) {
        return new CustomMaterial(id, protection, enchantability, equipSound::value, repairIngredient, toughness, knockbackResistance, 1);
    }

    public static CustomMaterial material(Identifier id, Map<ArmorItem.Type, Integer> protection, int enchantability,
                                          SoundEvent equipSound, Supplier<Ingredient> repairIngredient,
                                          float toughness, float knockbackResistance) {
        return new CustomMaterial(id, protection, enchantability, () -> equipSound, repairIngredient, toughness, knockbackResistance, 1);
    }

    // MARK: Items

    public static class CustomItem extends ArmorItem implements ConfigurableAttributes {
        private ItemAttributeModifiers attributeModifiers = ItemAttributeModifiers.DEFAULT;
        public final ArmorMaterial customMaterial;

        public CustomItem(ArmorMaterial material, Type slot, Settings settings) {
            super(material, slot, settings);
            this.customMaterial = material;
        }

        @Override
        public void setAttributes(ItemAttributeModifiers attributeModifiers) {
            this.attributeModifiers = attributeModifiers;
        }

        public ItemAttributeModifiers getConfiguredAttributeModifiers() {
            return this.attributeModifiers;
        }

        @Override
        public Multimap<EntityAttribute, EntityAttributeModifier> getAttributeModifiers(EquipmentSlot slot) {
            if (slot == this.getSlotType()) {
                return attributeModifiers.forSlot(slot);
            }
            return super.getAttributeModifiers(slot);
        }

        /// Identity of the armor texture set (the 1.21 first layer id): `<namespace>:<name>`
        public Identifier getFirstLayerId() {
            if (customMaterial instanceof CustomMaterial custom) {
                return custom.id;
            }
            return Identifier.tryParse(customMaterial.getName()) != null
                    ? new Identifier(customMaterial.getName())
                    : new Identifier("minecraft", customMaterial.getName());
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
            return new Identifier(namespace, name);
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

        public interface ItemFactory<T extends ArmorItem> {
            T create(ArmorMaterial material, ArmorItem.Type slot, Item.Settings settings);
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
                    .maxDamage(maxDamage(ArmorItem.Type.HELMET, durability));
            var chestplateSettings = new Item.Settings()
                    .maxDamage(maxDamage(ArmorItem.Type.CHESTPLATE, durability));
            var leggingsSettings = new Item.Settings()
                    .maxDamage(maxDamage(ArmorItem.Type.LEGGINGS, durability));
            var bootsSettings = new Item.Settings()
                    .maxDamage(maxDamage(ArmorItem.Type.BOOTS, durability));
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

        /// The 1.21 `ArmorItem.Type#getMaxDamage(int)`: base durability per piece × multiplier
        public static int maxDamage(ArmorItem.Type type, int durabilityMultiplier) {
            return CustomMaterial.BASE_DURABILITY.get(type) * durabilityMultiplier;
        }

        public Entry translatedName(String headName, String chestName, String legsName, String feetName) {
            armorSet.translate(headName, chestName, legsName, feetName);
            return this;
        }

        public String name() {
            return armorSet.name;
        }

        public <T extends ArmorItem> Entry bundle(Function<ArmorMaterial, Armor.Set<T>> factory) {
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
                var armorPiece = (ArmorItem) piece;
                AttributeModifierUtil.setItemModifiers(armorPiece, attributesFrom(config, armorPiece.getType()));
            }
            entry.armorSet().register(itemGroupKey);
        }
    }

    /// Vanilla's per-slot armor modifier UUIDs (`ArmorItem.MODIFIERS`, private in 1.20.1), so custom armor stacks
    /// exactly like vanilla armor in the same slot
    public static UUID slotModifierId(ArmorItem.Type slot) {
        return switch (slot) {
            case BOOTS -> UUID.fromString("845DB27C-C624-495F-8C9F-6020A9A58B6B");
            case LEGGINGS -> UUID.fromString("D8499B04-0E66-4726-AB29-64469D734E0D");
            case CHESTPLATE -> UUID.fromString("9F3D476D-C118-4544-8365-64846904B48E");
            case HELMET -> UUID.fromString("2AD3F246-FEE1-4E67-B886-69FD380BB150");
        };
    }

    private static ItemAttributeModifiers attributesFrom(ArmorSetConfig config, ArmorItem.Type slot) {
        ArmorSetConfig.Piece piece = null;
        var modifierId = slotModifierId(slot);
        var modifierName = "Armor modifier";
        switch (slot) {
            case BOOTS -> {
                piece = config.feet;
            }
            case LEGGINGS -> {
                piece = config.legs;
            }
            case CHESTPLATE -> {
                piece = config.chest;
            }
            case HELMET -> {
                piece = config.head;
            }
        }

        var builder = ItemAttributeModifiers.builder();
        var attributeModifierSlot = ItemAttributeModifiers.Slot.forEquipmentSlot(slot.getEquipmentSlot());

        if (config.armor_toughness != 0) {
            builder.add(EntityAttributes.GENERIC_ARMOR_TOUGHNESS,
                    new EntityAttributeModifier(
                            modifierId,
                            modifierName,
                            config.armor_toughness,
                            EntityAttributeModifier.Operation.ADDITION),
                    attributeModifierSlot);
        }
        if (config.knockback_resistance != 0) {
            builder.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE,
                    new EntityAttributeModifier(
                            modifierId,
                            modifierName,
                            config.knockback_resistance,
                            EntityAttributeModifier.Operation.ADDITION),
                    attributeModifierSlot);
        }
        if (piece.armor != 0) {
            builder.add(EntityAttributes.GENERIC_ARMOR,
                    new EntityAttributeModifier(
                            modifierId,
                            modifierName,
                            piece.armor,
                            EntityAttributeModifier.Operation.ADDITION),
                    attributeModifierSlot);
        }
        for (var attribute: piece.selectedAttributes()) {
            try {
                var attributeId = new Identifier(attribute.attribute);
                var entityAttribute = AttributeModifierUtil.attributeEntry(attributeId)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown attribute: " + attributeId));
                builder.add(entityAttribute,
                        new EntityAttributeModifier(
                                modifierId,
                                modifierName,
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
