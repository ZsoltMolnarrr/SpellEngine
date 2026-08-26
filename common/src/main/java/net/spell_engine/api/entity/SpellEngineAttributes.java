package net.spell_engine.api.entity;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
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
        public final Attribute attribute;
        public final double baseValue;
        @Nullable
        public Holder<Attribute> entry;

        public Entry(String name, double minValue, double baseValue, boolean tracked) {
            this.id = Identifier.fromNamespaceAndPath(NAMESPACE, name);
            this.translationKey = "attribute.name." + NAMESPACE + "." + name;
            this.attribute = new RangedAttribute(translationKey, baseValue, minValue, 2048).setSyncable(tracked);
            this.baseValue = baseValue;
        }

        public Entry category(Attribute.Sentiment category) {
            attribute.setSentiment(category);
            return this;
        }

        public double asMultiplier(double attributeValue) {
            return attributeValue / baseValue;
        }

        public double asChance(double attributeValue) {
            return (attributeValue - baseValue) / baseValue;
        }

        public void register() {
            entry = Registry.registerForHolder(BuiltInRegistries.ATTRIBUTE, id, attribute);
        }
    }

    public static Entry HEALING_TAKEN = add(new Entry("healing_taken", 0, 100, false));
    public static Entry DAMAGE_TAKEN = add(new Entry("damage_taken", 0, 100, false))
            .category(Attribute.Sentiment.NEGATIVE);
    public static Entry EVASION_CHANCE = add(new Entry("evasion_chance", 0, 100, false));

    public static void register() {
        all.forEach(Entry::register);
    }
}
