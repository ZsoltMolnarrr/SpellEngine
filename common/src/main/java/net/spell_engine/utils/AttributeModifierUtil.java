package net.spell_engine.utils;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.StringIdentifiable;
import net.spell_engine.api.item.ItemAttributeModifiers;
import net.spell_engine.rpg_series.item.ConfigurableAttributes;
import net.spell_power.api.ModifierDefinitions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/// The single seam through which Spell Engine (and the RPG Series item builders) create, register and read
/// item attribute modifiers on 1.20.1.
///
/// - Modifiers are UUID-keyed here; every modern `Identifier` id goes through {@link #modifier(Identifier, double, EntityAttributeModifier.Operation)}
///   (UUID = `ModifierDefinitions.uuid(id)`, name = `id.toString()`), so ids stay stable and reversible.
/// - Per-item modifier defaults (the 1.21 `Item.Settings#attributeModifiers(...)`) are registered with
///   {@link #setItemModifiers(Item, ItemAttributeModifiers)} and served by `ItemStackAttributeModifiersMixin`
///   for any item class, and directly by items implementing {@link ConfigurableAttributes}.
public class AttributeModifierUtil {

    // MARK: Modifier construction

    public static EntityAttributeModifier modifier(Identifier id, double value, EntityAttributeModifier.Operation operation) {
        return new EntityAttributeModifier(ModifierDefinitions.uuid(id), ModifierDefinitions.name(id), value, operation);
    }

    public static EntityAttributeModifier modifier(UUID uuid, String name, double value, EntityAttributeModifier.Operation operation) {
        return new EntityAttributeModifier(uuid, name, value, operation);
    }

    /// The `Identifier` a modifier was created from (its name), falling back to a namespaced form of the name
    public static Identifier idOf(EntityAttributeModifier modifier) {
        var name = modifier.getName();
        var id = Identifier.tryParse(name);
        if (id != null) {
            return id;
        }
        var path = name.toLowerCase().replaceAll("[^a-z0-9/._-]", "_");
        return new Identifier("spell_engine", "modifier/" + path);
    }

    /// Operation names as 1.21 spells them (`add_value`, `add_multiplied_base`, `add_multiplied_total`),
    /// plus the 1.20.1 names as aliases, for JSON authored either way
    public enum Operations implements StringIdentifiable {
        ADD_VALUE("add_value", EntityAttributeModifier.Operation.ADDITION),
        ADD_MULTIPLIED_BASE("add_multiplied_base", EntityAttributeModifier.Operation.MULTIPLY_BASE),
        ADD_MULTIPLIED_TOTAL("add_multiplied_total", EntityAttributeModifier.Operation.MULTIPLY_TOTAL),
        ADDITION("addition", EntityAttributeModifier.Operation.ADDITION),
        MULTIPLY_BASE("multiply_base", EntityAttributeModifier.Operation.MULTIPLY_BASE),
        MULTIPLY_TOTAL("multiply_total", EntityAttributeModifier.Operation.MULTIPLY_TOTAL);

        public final String id;
        public final EntityAttributeModifier.Operation operation;

        Operations(String id, EntityAttributeModifier.Operation operation) {
            this.id = id;
            this.operation = operation;
        }

        @Override
        public String asString() {
            return id;
        }

        public static Operations of(EntityAttributeModifier.Operation operation) {
            return switch (operation) {
                case ADDITION -> ADD_VALUE;
                case MULTIPLY_BASE -> ADD_MULTIPLIED_BASE;
                case MULTIPLY_TOTAL -> ADD_MULTIPLIED_TOTAL;
            };
        }

        private static final com.mojang.serialization.Codec<Operations> NAME_CODEC = StringIdentifiable.createCodec(Operations::values);
        /// `EntityAttributeModifier.Operation` as a string, 1.21-named
        public static final com.mojang.serialization.Codec<EntityAttributeModifier.Operation> CODEC = NAME_CODEC.xmap(named -> named.operation, Operations::of);
    }

    // MARK: Attribute resolution

    public static Optional<RegistryEntry<EntityAttribute>> attributeEntry(Identifier id) {
        return Registries.ATTRIBUTE.getEntry(RegistryKey.of(RegistryKeys.ATTRIBUTE, id)).map(entry -> (RegistryEntry<EntityAttribute>) entry);
    }

    public static Optional<EntityAttribute> attribute(Identifier id) {
        return Registries.ATTRIBUTE.getOrEmpty(id);
    }

    // MARK: Per-item modifier defaults (replaces `Item.Settings#attributeModifiers`)

    private static final Map<Item, ItemAttributeModifiers> itemModifiers = new HashMap<>();

    /// Registers the attribute modifiers every stack of `item` carries (unless the stack has its own
    /// `AttributeModifiers` NBT). Items implementing {@link ConfigurableAttributes} are also handed the value directly.
    public static void setItemModifiers(Item item, ItemAttributeModifiers modifiers) {
        itemModifiers.put(item, modifiers);
        if (item instanceof ConfigurableAttributes configurable) {
            configurable.setAttributes(modifiers);
        }
    }

    @Nullable
    public static ItemAttributeModifiers itemModifiers(Item item) {
        return itemModifiers.get(item);
    }

    public static boolean hasItemModifiers(Item item) {
        return itemModifiers.containsKey(item);
    }

    // MARK: Reading modifiers off item stacks

    /// Every attribute modifier of the stack, across all equipment slots
    public static @NotNull Multimap<EntityAttribute, EntityAttributeModifier> modifierMultimap(ItemStack itemStack) {
        Multimap<EntityAttribute, EntityAttributeModifier> modifiersMap = HashMultimap.create();
        if (itemStack.isEmpty()) {
            return modifiersMap;
        }
        for (var slot : EquipmentSlot.values()) {
            modifiersMap.putAll(itemStack.getAttributeModifiers(slot));
        }
        return modifiersMap;
    }

    /// Attribute modifiers the stack applies in a given slot
    public static @NotNull Multimap<EntityAttribute, EntityAttributeModifier> modifierMultimap(ItemStack itemStack, EquipmentSlot slot) {
        if (itemStack.isEmpty()) {
            return HashMultimap.create();
        }
        return itemStack.getAttributeModifiers(slot);
    }

    public static boolean hasModifier(ItemStack itemStack, EntityAttribute attribute) {
        return modifierMultimap(itemStack).containsKey(attribute);
    }

    public static boolean hasModifier(ItemStack itemStack, RegistryEntry<EntityAttribute> attribute) {
        return hasModifier(itemStack, attribute.value());
    }

    public static double flatBonusFrom(ItemStack itemStack, EntityAttribute attribute) {
        double value = 0;
        for (var modifier : modifierMultimap(itemStack).get(attribute)) {
            if (modifier.getOperation() == EntityAttributeModifier.Operation.ADDITION) {
                value += modifier.getValue();
            }
        }
        return value;
    }

    public static double flatBonusFrom(ItemStack itemStack, RegistryEntry<EntityAttribute> attribute) {
        return flatBonusFrom(itemStack, attribute.value());
    }

    /// Whether the item speaks for a hand slot (a weapon or tool) rather than an armor slot
    public static boolean isHeldEquipment(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return !stack.getAttributeModifiers(EquipmentSlot.MAINHAND).isEmpty()
                || !stack.getAttributeModifiers(EquipmentSlot.OFFHAND).isEmpty();
    }

    // MARK: Reading modifiers off entities

    public static double multipliersOf(EntityAttribute attribute, LivingEntity entity) {
        double value = 1;
        double totalMultiplier = 1;
        var attributeInstance = entity.getAttributes().getCustomInstance(attribute);
        if (attributeInstance != null) {
            for (var modifier: attributeInstance.getModifiers()) {
                switch (modifier.getOperation()) {
                    case ADDITION -> {
                        break;
                    }
                    case MULTIPLY_BASE -> {
                        value += modifier.getValue();
                    }
                    case MULTIPLY_TOTAL -> {
                        totalMultiplier += modifier.getValue();
                    }
                }
            }
        }
        return value * totalMultiplier;
    }

    public static double multipliersOf(RegistryEntry<EntityAttribute> attribute, LivingEntity entity) {
        return multipliersOf(attribute.value(), entity);
    }

    public static boolean isItemStackEquipped(ItemStack itemStack, PlayerEntity player) {
        if (player.getMainHandStack().equals(itemStack)) {
            return true;
        }
        for (var armorSlot: player.getInventory().armor) {
            if (armorSlot.equals(itemStack)) {
                return true;
            }
        }
        for (var offhandSlot: player.getInventory().offHand) {
            if (offhandSlot.equals(itemStack)) {
                return true;
            }
        }
        return false;
    }

    // MARK: Tooltip

    /// One vanilla-styled tooltip line for a modifier (`+5% Attack Damage` / `-2 Armor`), the 1.20.1 equivalent of
    /// `ItemStack#appendAttributeModifierTooltip` (which is inlined into `getTooltip` on this version)
    public static Text tooltipLine(EntityAttribute attribute, EntityAttributeModifier modifier) {
        double value = modifier.getValue();
        double displayed;
        if (modifier.getOperation() == EntityAttributeModifier.Operation.MULTIPLY_BASE
                || modifier.getOperation() == EntityAttributeModifier.Operation.MULTIPLY_TOTAL) {
            displayed = value * 100.0;
        } else if (attribute.equals(net.minecraft.entity.attribute.EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE)) {
            displayed = value * 10.0;
        } else {
            displayed = value;
        }
        var attributeName = Text.translatable(attribute.getTranslationKey());
        if (value < 0) {
            return Text.translatable(
                    "attribute.modifier.take." + modifier.getOperation().getId(),
                    ItemStack.MODIFIER_FORMAT.format(-displayed),
                    attributeName
            ).formatted(Formatting.RED);
        }
        return Text.translatable(
                "attribute.modifier.plus." + modifier.getOperation().getId(),
                ItemStack.MODIFIER_FORMAT.format(displayed),
                attributeName
        ).formatted(Formatting.BLUE);
    }

    public static Text tooltipLine(RegistryEntry<EntityAttribute> attribute, EntityAttributeModifier modifier) {
        return tooltipLine(attribute.value(), modifier);
    }
}
