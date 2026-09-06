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
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.StringIdentifiable;
import net.spell_engine.utils.AttributeModifierUtil;
import net.spell_engine.utils.AttributeModifierUtil.Operations;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/// 1.20.1 stand-in for the 1.21 `AttributeModifiersComponent`: a slot-tagged list of attribute modifiers.
///
/// Serializes with the exact 1.21 JSON shape
/// (`{"modifiers": [{"type": "<attribute id>", "id": "<modifier id>", "amount": 1.0, "operation": "add_value", "slot": "armor"}]}`),
/// so datapack content authored for 1.21 (equipment sets) keeps loading. Modifier ids are `Identifier`s in JSON and become
/// UUID + name through {@link AttributeModifierUtil#modifier(Identifier, double, EntityAttributeModifier.Operation)};
/// the name carries the id back for serialization.
///
/// **Attributes are carried by id, not by registry entry.** An {@link Entry} stores the attribute's `Identifier` and
/// resolves it against `Registries.ATTRIBUTE` lazily, so:
/// - *encode* always writes the id, whether or not the attribute is registered — datagen can emit a modifier for an
///   optional third-party attribute (e.g. `ranged_weapon:damage`) without that mod on the datagen runtime;
/// - *decode* never fails on an unknown attribute id: the entry is kept **unresolved** ({@link Entry#isResolved()} is
///   `false`) and round-trips back to the same JSON, while {@link #forSlot(EquipmentSlot)} / {@link #all()} — and
///   every runtime consumer — skip it. The rest of the list still applies. (Malformed entries — a bad `operation`
///   or `slot` string — still fail the codec, and `EquipmentSet.Bonus`'s `optionalFieldOf("attributes")` then drops
///   the whole block as before.)
public record ItemAttributeModifiers(List<Entry> modifiers, boolean showInTooltip) {

    public static final ItemAttributeModifiers DEFAULT = new ItemAttributeModifiers(List.of(), true);

    /// One modifier, keyed by the attribute's registry id.
    ///
    /// {@link #attribute()} / {@link #attributeValue()} resolve the id against the live attribute registry on every
    /// call (a hash lookup) and return `null` while the attribute is not registered.
    public record Entry(Identifier attributeId, EntityAttributeModifier modifier, Slot slot) {
        public Entry(RegistryEntry<EntityAttribute> attribute, EntityAttributeModifier modifier, Slot slot) {
            this(idOf(attribute), modifier, slot);
        }

        public Entry(EntityAttribute attribute, EntityAttributeModifier modifier, Slot slot) {
            this(idOf(attribute), modifier, slot);
        }

        private static Identifier idOf(RegistryEntry<EntityAttribute> attribute) {
            var key = attribute.getKey();
            if (key.isPresent()) {
                return key.get().getValue();
            }
            return idOf(attribute.value());
        }

        private static Identifier idOf(EntityAttribute attribute) {
            var id = Registries.ATTRIBUTE.getId(attribute);
            if (id == null) {
                throw new IllegalArgumentException("Attribute is not registered (no id): " + attribute.getTranslationKey()
                        + " — use the Identifier-based constructor for attributes that are registered later");
            }
            return id;
        }

        /// The live registry entry of the attribute, or `null` while `attributeId` is not registered.
        @Nullable
        public RegistryEntry<EntityAttribute> attribute() {
            return Registries.ATTRIBUTE.getEntry(RegistryKey.of(RegistryKeys.ATTRIBUTE, attributeId))
                    .map(entry -> (RegistryEntry<EntityAttribute>) entry)
                    .orElse(null);
        }

        /// The raw attribute, or `null` while `attributeId` is not registered.
        @Nullable
        public EntityAttribute attributeValue() {
            return Registries.ATTRIBUTE.get(attributeId);
        }

        /// `true` when `attributeId` is currently registered (the entry contributes to {@link #forSlot} / {@link #all}).
        public boolean isResolved() {
            return Registries.ATTRIBUTE.containsId(attributeId);
        }

        /// The 1.21 inline modifier fields: `id`, `amount`, `operation`
        private static final MapCodec<EntityAttributeModifier> MODIFIER_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Identifier.CODEC.fieldOf("id").forGetter(modifier -> AttributeModifierUtil.idOf(modifier)),
                Codec.DOUBLE.fieldOf("amount").forGetter(EntityAttributeModifier::getValue),
                Operations.CODEC.fieldOf("operation").forGetter(EntityAttributeModifier::getOperation)
        ).apply(instance, AttributeModifierUtil::modifier));

        /// `type` is a plain `Identifier` (not `Registries.ATTRIBUTE.createEntryCodec()`, whose encode side
        /// dereferences the live entry): unknown ids encode and decode as-is, see the class docs.
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("type").forGetter(Entry::attributeId),
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
            entries.add(new Entry(attribute, modifier, slot));
            return this;
        }

        /// Id-only escape hatch: the attribute does not have to be registered (now, or ever, on this runtime).
        /// The entry serializes normally and only takes effect on runtimes where `attributeId` resolves.
        public Builder add(Identifier attributeId, EntityAttributeModifier modifier, Slot slot) {
            entries.add(new Entry(attributeId, modifier, slot));
            return this;
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
        return with(new Entry(attribute, modifier, slot));
    }

    public ItemAttributeModifiers with(Identifier attributeId, EntityAttributeModifier modifier, Slot slot) {
        return with(new Entry(attributeId, modifier, slot));
    }

    private ItemAttributeModifiers with(Entry entry) {
        var list = new ArrayList<>(modifiers);
        list.add(entry);
        return new ItemAttributeModifiers(List.copyOf(list), showInTooltip);
    }

    /// The modifiers this list applies in `slot`, in the shape `Item#getAttributeModifiers(EquipmentSlot)` returns.
    /// Entries whose attribute is not registered on this runtime are skipped.
    public Multimap<EntityAttribute, EntityAttributeModifier> forSlot(EquipmentSlot slot) {
        ImmutableMultimap.Builder<EntityAttribute, EntityAttributeModifier> builder = ImmutableMultimap.builder();
        for (var entry : modifiers) {
            if (!entry.slot().matches(slot)) {
                continue;
            }
            var attribute = entry.attributeValue();
            if (attribute != null) {
                builder.put(attribute, entry.modifier());
            }
        }
        return builder.build();
    }

    /// Every modifier regardless of slot. Entries whose attribute is not registered on this runtime are skipped.
    public Multimap<EntityAttribute, EntityAttributeModifier> all() {
        ImmutableMultimap.Builder<EntityAttribute, EntityAttributeModifier> builder = ImmutableMultimap.builder();
        for (var entry : modifiers) {
            var attribute = entry.attributeValue();
            if (attribute != null) {
                builder.put(attribute, entry.modifier());
            }
        }
        return builder.build();
    }
}
