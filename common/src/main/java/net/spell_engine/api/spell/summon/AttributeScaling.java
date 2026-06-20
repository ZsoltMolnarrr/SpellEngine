package net.spell_engine.api.spell.summon;

import net.minecraft.entity.attribute.EntityAttributeModifier;

import java.util.ArrayList;
import java.util.List;

/// Owner-scaled attribute bonuses applied to a summoned entity. Each {@link Entry} targets one of
/// the summon's attributes and sums one or more {@link Entry.OwnerModifier}s computed from the
/// owner's attributes (`base + ownerValue * coefficient`).
public class AttributeScaling {
    public List<Entry> entries = new ArrayList<>();

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
