package net.spell_engine.api.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.StringIdentifiable;
import net.spell_engine.utils.AttributeModifierUtil;
import net.spell_engine.utils.AttributeModifierUtil.Operations;

import java.util.ArrayList;
import java.util.List;

/// 1.20.1 stand-in for the 1.21 `AttributeModifiersComponent`: a slot-tagged list of attribute modifiers.
///
/// Serializes with the exact 1.21 JSON shape
/// (`{"modifiers": [{"type": "<attribute id>", "id": "<modifier id>", "amount": 1.0, "operation": "add_value", "slot": "armor"}]}`),
/// so datapack content authored for 1.21 (equipment sets) keeps loading. Modifier ids are `Identifier`s in JSON and become
/// UUID + name through {@link AttributeModifierUtil#modifier(Identifier, double, EntityAttributeModifier.Operation)};
/// the name carries the id back for serialization.
public record ItemAttributeModifiers(List<Entry> modifiers, boolean showInTooltip) {

    public static final ItemAttributeModifiers DEFAULT = new ItemAttributeModifiers(List.of(), true);

    public record Entry(RegistryEntry<EntityAttribute> attribute, EntityAttributeModifier modifier, Slot slot) {
        /// The 1.21 inline modifier fields: `id`, `amount`, `operation`
        private static final MapCodec<EntityAttributeModifier> MODIFIER_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Identifier.CODEC.fieldOf("id").forGetter(modifier -> AttributeModifierUtil.idOf(modifier)),
                Codec.DOUBLE.fieldOf("amount").forGetter(EntityAttributeModifier::getValue),
                Operations.CODEC.fieldOf("operation").forGetter(EntityAttributeModifier::getOperation)
        ).apply(instance, AttributeModifierUtil::modifier));

        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Registries.ATTRIBUTE.createEntryCodec().fieldOf("type").forGetter(Entry::attribute),
                MODIFIER_CODEC.forGetter(Entry::modifier),
                Slot.CODEC.optionalFieldOf("slot", Slot.ANY).forGetter(Entry::slot)
        ).apply(instance, Entry::new));
    }

    public static final Codec<ItemAttributeModifiers> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Entry.CODEC.listOf().fieldOf("modifiers").forGetter(ItemAttributeModifiers::modifiers),
            Codec.BOOL.optionalFieldOf("show_in_tooltip", true).forGetter(ItemAttributeModifiers::showInTooltip)
    ).apply(instance, ItemAttributeModifiers::new));

    /// Mirrors the 1.21 `AttributeModifierSlot`
    public enum Slot implements StringIdentifiable {
        ANY("any", slot -> true),
        MAINHAND("mainhand", slot -> slot == EquipmentSlot.MAINHAND),
        OFFHAND("offhand", slot -> slot == EquipmentSlot.OFFHAND),
        HAND("hand", slot -> slot.getType() == EquipmentSlot.Type.HAND),
        FEET("feet", slot -> slot == EquipmentSlot.FEET),
        LEGS("legs", slot -> slot == EquipmentSlot.LEGS),
        CHEST("chest", slot -> slot == EquipmentSlot.CHEST),
        HEAD("head", slot -> slot == EquipmentSlot.HEAD),
        ARMOR("armor", slot -> slot.getType() == EquipmentSlot.Type.ARMOR);

        public static final Codec<Slot> CODEC = StringIdentifiable.createCodec(Slot::values);

        private final String name;
        private final java.util.function.Predicate<EquipmentSlot> predicate;

        Slot(String name, java.util.function.Predicate<EquipmentSlot> predicate) {
            this.name = name;
            this.predicate = predicate;
        }

        public boolean matches(EquipmentSlot slot) {
            return predicate.test(slot);
        }

        public static Slot forEquipmentSlot(EquipmentSlot slot) {
            return switch (slot) {
                case MAINHAND -> MAINHAND;
                case OFFHAND -> OFFHAND;
                case FEET -> FEET;
                case LEGS -> LEGS;
                case CHEST -> CHEST;
                case HEAD -> HEAD;
            };
        }

        @Override
        public String asString() {
            return name;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final List<Entry> entries = new ArrayList<>();

        public Builder add(RegistryEntry<EntityAttribute> attribute, EntityAttributeModifier modifier, Slot slot) {
            entries.add(new Entry(attribute, modifier, slot));
            return this;
        }

        public Builder add(EntityAttribute attribute, EntityAttributeModifier modifier, Slot slot) {
            return add(Registries.ATTRIBUTE.getEntry(attribute), modifier, slot);
        }

        public Builder addAll(ItemAttributeModifiers other) {
            entries.addAll(other.modifiers());
            return this;
        }

        public ItemAttributeModifiers build() {
            return new ItemAttributeModifiers(List.copyOf(entries), true);
        }
    }

    public boolean isEmpty() {
        return modifiers.isEmpty();
    }

    public ItemAttributeModifiers with(RegistryEntry<EntityAttribute> attribute, EntityAttributeModifier modifier, Slot slot) {
        var list = new ArrayList<>(modifiers);
        list.add(new Entry(attribute, modifier, slot));
        return new ItemAttributeModifiers(List.copyOf(list), showInTooltip);
    }

    /// The modifiers this list applies in `slot`, in the shape `Item#getAttributeModifiers(EquipmentSlot)` returns
    public Multimap<EntityAttribute, EntityAttributeModifier> forSlot(EquipmentSlot slot) {
        ImmutableMultimap.Builder<EntityAttribute, EntityAttributeModifier> builder = ImmutableMultimap.builder();
        for (var entry : modifiers) {
            if (entry.slot().matches(slot)) {
                builder.put(entry.attribute().value(), entry.modifier());
            }
        }
        return builder.build();
    }

    /// Every modifier regardless of slot
    public Multimap<EntityAttribute, EntityAttributeModifier> all() {
        ImmutableMultimap.Builder<EntityAttribute, EntityAttributeModifier> builder = ImmutableMultimap.builder();
        for (var entry : modifiers) {
            builder.put(entry.attribute().value(), entry.modifier());
        }
        return builder.build();
    }
}
