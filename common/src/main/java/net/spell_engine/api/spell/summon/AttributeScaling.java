package net.spell_engine.api.spell.summon;

import net.minecraft.entity.attribute.EntityAttributeModifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Owner-scaled attribute bonuses applied to a summoned entity. Each {@link Entry} targets one of
/// the summon's attributes and sums one or more {@link Entry.OwnerModifier}s computed from the
/// owner's attributes (`base + ownerValue * coefficient`).
public class AttributeScaling {
    public List<Entry> entries = new ArrayList<>();

    /// Returns a new {@link AttributeScaling} combining the entries of {@code a} and {@code b},
    /// grouped by {@code attribute_id} so entries targeting the same attribute have their owner
    /// modifiers concatenated (i.e. their scaling sums). Neither input is mutated; {@code null}
    /// inputs are treated as empty. The resulting entries hold the original {@link Entry.OwnerModifier}
    /// references (they are read-only at apply time), so no deep copy is performed.
    public static AttributeScaling merged(@Nullable AttributeScaling a, @Nullable AttributeScaling b) {
        Map<String, Entry> byId = new LinkedHashMap<>();
        collectInto(byId, a);
        collectInto(byId, b);
        var result = new AttributeScaling();
        result.entries = new ArrayList<>(byId.values());
        return result;
    }

    private static void collectInto(Map<String, Entry> byId, @Nullable AttributeScaling source) {
        if (source == null) return;
        for (var entry : source.entries) {
            var target = byId.computeIfAbsent(entry.attribute_id, id -> {
                var e = new Entry();
                e.attribute_id = id;
                return e;
            });
            target.modifiers.addAll(entry.modifiers);
        }
    }

    public static class Entry {
        public String attribute_id = "";
        public List<OwnerModifier> modifiers = new ArrayList<>();

        public static class OwnerModifier {
            public String attribute_id = "";
            public EntityAttributeModifier.Operation operation = EntityAttributeModifier.Operation.ADD_VALUE;
            /// Flat amount added before the owner-scaled term. Final contribution is
            /// `base + ownerValue * coefficient`.
            public double base = 0.0;
            public double coefficient = 1.0;

            public OwnerModifier() {}

            public OwnerModifier(String attribute_id, EntityAttributeModifier.Operation operation, double coefficient) {
                this(attribute_id, operation, 0.0, coefficient);
            }

            public OwnerModifier(String attribute_id, EntityAttributeModifier.Operation operation, double base, double coefficient) {
                this.attribute_id = attribute_id;
                this.operation = operation;
                this.base = base;
                this.coefficient = coefficient;
            }
        }
    }
}
