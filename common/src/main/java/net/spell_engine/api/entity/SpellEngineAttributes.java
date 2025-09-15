package net.spell_engine.api.entity;

import net.minecraft.entity.attribute.ClampedEntityAttribute;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.spell_engine.SpellEngineMod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class SpellEngineAttributes {
    public static final String NAMESPACE = SpellEngineMod.ID;
    public static final ArrayList<Entry> all = new ArrayList<>();
    public static Entry add(Entry entry) {
        all.add(entry);
        return entry;
    }

    public static class Entry {
        public final Identifier id;
        public final String translationKey;
        public final EntityAttribute attribute;
        public final double baseValue;
        @Nullable
        public RegistryEntry<EntityAttribute> entry;

        public Entry(String name, double minValue, double baseValue, boolean tracked) {
            this.id = Identifier.of(NAMESPACE, name);
            this.translationKey = "attribute.name." + NAMESPACE + "." + name;
            this.attribute = new ClampedEntityAttribute(translationKey, baseValue, minValue, 2048).setTracked(tracked);
            this.baseValue = baseValue;
        }

        public Entry category(EntityAttribute.Category category) {
            attribute.setCategory(category);
            return this;
        }

        public double asMultiplier(double attributeValue) {
            return attributeValue / baseValue;
        }

        public double asChance(double attributeValue) {
            return (attributeValue - baseValue) / baseValue;
        }

        public void register() {
            entry = Registry.registerReference(Registries.ATTRIBUTE, id, attribute);
        }
    }

    public static Entry HEALING_TAKEN = add(new Entry("healing_taken", 0, 100, false));
    public static Entry DAMAGE_TAKEN = add(new Entry("damage_taken", 0, 100, false))
            .category(EntityAttribute.Category.NEGATIVE);
    public static Entry EVASION_CHANCE = add(new Entry("evasion_chance", 0, 100, false));

    public static void register() {
        all.forEach(Entry::register);
    }
}
