package net.spell_engine.api.item.armor;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.recipe.Ingredient;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.Lazy;
import net.minecraft.util.registry.Registry;
import net.spell_engine.api.item.ConfigurableAttributes;
import net.spell_engine.api.item.ItemConfig;
import net.spell_power.api.attributes.SpellAttributes;
import net.spell_power.api.enchantment.SpellPowerEnchanting;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Armor {
    public static class Set<A extends ArmorItem> {
        public final String namespace;
        public final A head, chest, legs, feet;
        public Set(String namespace, A head, A chest, A legs, A feet) {
            this.namespace = namespace;
            this.head = head;
            this.chest = chest;
            this.legs = legs;
            this.feet = feet;
        }
        public List<A> pieces() {
            return Stream.of(head, chest, legs, feet).filter(Objects::nonNull).collect(Collectors.toList());
        }

        public Identifier idOf(ArmorItem piece) {
            var name = piece.getMaterial().getName() + "_" + piece.getSlotType().getName();
            return new Identifier(namespace, name);
        }

        public List<String> idStrings() {
            return pieces().stream().map(piece -> idOf(piece).toString()).toList();
        }

        public void register() {
            for (var piece: pieces()) {
                Registry.register(Registry.ITEM, idOf(piece), piece);
                SpellPowerEnchanting.registerArmor(piece);
            }
        }
    }

    public record Entry(CustomMaterial material, Armor.Set armorSet, ItemConfig.ArmorSet defaults) {
        public String name() {
            return material.getName();
        }
        public <T extends ArmorItem> Armor.Set<T> armorSet(Function<CustomMaterial, Armor.Set<T>> factory, ArrayList<Entry> entries) {
            var armorSet = factory.apply(material);
            entries.add(new Entry(material, armorSet, defaults));
            return armorSet;
        }
    }

    public static class CustomMaterial implements ArmorMaterial {
        private static final int[] BASE_DURABILITY = new int[]{13, 15, 16, 11};
        private final String name;
        private final int durabilityMultiplier;
        private final int enchantability;
        private final SoundEvent equipSound;
        private final Lazy<Ingredient> repairIngredientSupplier;


        // MARK: Configurables
        private int[] armor;
        private float toughness;
        private float knockbackResistance;

        public CustomMaterial(String name, int durabilityMultiplier, int enchantability, SoundEvent equipSound, Supplier<Ingredient> repairIngredientSupplier) {
            this.name = name;
            this.durabilityMultiplier = durabilityMultiplier;
            this.enchantability = enchantability;
            this.equipSound = equipSound;
            this.repairIngredientSupplier = new Lazy(repairIngredientSupplier);

            this.armor = new int[]{0, 0, 0, 0};
            this.toughness = 0;
            this.knockbackResistance = 0;
        }

        public int getDurability(EquipmentSlot slot) {
            return BASE_DURABILITY[slot.getEntitySlotId()] * this.durabilityMultiplier;
        }

        public int getEnchantability() {
            return this.enchantability;
        }

        public SoundEvent getEquipSound() {
            return this.equipSound;
        }

        public Ingredient getRepairIngredient() {
            return (Ingredient)this.repairIngredientSupplier.get();
        }

        public String name() {
            return this.name;
        }

        // Avoid using this, because this gets remapped
        @Deprecated
        public String getName() {
            return this.name;
        }

        public int getProtectionAmount(EquipmentSlot slot) {
            return this.armor[slot.getEntitySlotId()];
        }

        public float getToughness() {
            return this.toughness;
        }

        public float getKnockbackResistance() {
            return this.knockbackResistance;
        }

        public void configure(ItemConfig.ArmorSet config) {
            this.toughness = config.armor_toughness;
            this.knockbackResistance = config.knockback_resistance;
            this.armor = new int[]{config.head.armor, config.chest.armor, config.legs.armor, config.feet.armor};
        }
    }


    // MARK: Registration


    public static void register(Map<String, ItemConfig.ArmorSet> configs, List<Entry> entries) {
        for(var entry: entries) {
            var config = configs.get(entry.name());
            if (config == null) {
                config = entry.defaults();
                configs.put(entry.name(), config);
            }
            entry.material().configure(config);
            for (var piece: entry.armorSet().pieces()) {
                var slot = ((ArmorItem)piece).getSlotType();
                ((ConfigurableAttributes)piece).setAttributes(attributesFrom(config, slot));
            }
            entry.armorSet().register();
        }
    }

    private static Multimap<EntityAttribute, EntityAttributeModifier> attributesFrom(ItemConfig.ArmorSet config, EquipmentSlot slot) {
        ItemConfig.ArmorSet.Piece piece = null;
        UUID uuid = MODIFIERS[slot.getEntitySlotId()];
        switch (slot) {
            case FEET -> {
                piece = config.feet;
            }
            case LEGS -> {
                piece = config.legs;
            }
            case CHEST -> {
                piece = config.chest;
            }
            case HEAD -> {
                piece = config.head;
            }
        }
        ImmutableMultimap.Builder<EntityAttribute, EntityAttributeModifier> builder = ImmutableMultimap.builder();
        if (config.armor_toughness != 0) {
            builder.put(EntityAttributes.GENERIC_ARMOR_TOUGHNESS,
                    new EntityAttributeModifier(
                            uuid,
                            "Armor modifier",
                            config.armor_toughness,
                            EntityAttributeModifier.Operation.ADDITION));
        }
        if (config.knockback_resistance != 0) {
            builder.put(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE,
                    new EntityAttributeModifier(
                            uuid,
                            "Armor modifier",
                            config.knockback_resistance,
                            EntityAttributeModifier.Operation.ADDITION));
        }
        if (piece.armor != 0) {
            builder.put(EntityAttributes.GENERIC_ARMOR,
                    new EntityAttributeModifier(
                            uuid,
                            "Armor modifier",
                            piece.armor,
                            EntityAttributeModifier.Operation.ADDITION));
        }
        for (var attribute: piece.spell_attributes) {
            try {
                var entityAttribute = SpellAttributes.all.get(attribute.name).attribute;
                builder.put(entityAttribute,
                        new EntityAttributeModifier(
                                uuid,
                                "Armor modifier",
                                attribute.value,
                                attribute.operation));
            } catch (Exception e) {
                System.err.println("Failed to add item attribute modifier: " + e.getMessage());
            }
        }

        return builder.build();
    }

    private static final UUID[] MODIFIERS = new UUID[]{
            UUID.fromString("845DB27C-C624-495F-8C9F-6020A9A58B6B"),
            UUID.fromString("D8499B04-0E66-4726-AB29-64469D734E0D"),
            UUID.fromString("9F3D476D-C118-4544-8365-64846904B48E"),
            UUID.fromString("2AD3F246-FEE1-4E67-B886-69FD380BB150")
    };
}
