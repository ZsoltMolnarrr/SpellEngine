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
import java.util.List;

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
        /// `null` until {@link #register()} ran (Forge: until the attribute `RegisterEvent`).
        /// 1.20.1 APIs that take a raw attribute should use {@link #attribute} instead.
        @Nullable
        public RegistryEntry<EntityAttribute> entry;

        public Entry(String name, double minValue, double baseValue, boolean tracked) {
            this.id = new Identifier(NAMESPACE, name);
            this.translationKey = "attribute.name." + NAMESPACE + "." + name;
            this.attribute = new ClampedEntityAttribute(translationKey, baseValue, minValue, 2048).setTracked(tracked);
            this.baseValue = baseValue;
        }

        public double asMultiplier(double attributeValue) {
            return attributeValue / baseValue;
        }

        public double asChance(double attributeValue) {
            return (attributeValue - baseValue) / baseValue;
        }

        /// Idempotent: safe to call from a `<clinit>` mixin (Fabric) and from `RegisterEvent` (Forge).
        public void register() {
            if (entry != null) { return; }
            if (Registries.ATTRIBUTE.containsId(id)) {
                entry = Registries.ATTRIBUTE.getEntry(Registries.ATTRIBUTE.get(id));
                return;
            }
            entry = Registry.registerReference(Registries.ATTRIBUTE, id, attribute);
        }
    }

    public static Entry HEALING_TAKEN = add(new Entry("healing_taken", 0, 100, false));
    public static Entry DAMAGE_TAKEN = add(new Entry("damage_taken", 0, 100, false));
    public static Entry EVASION_CHANCE = add(new Entry("evasion_chance", 0, 100, false));

    /// Idempotent registration of every SpellEngine attribute.
    public static void register() {
        all.forEach(Entry::register);
    }

    /// Raw attributes that every living entity's default attribute container must receive.
    /// Fabric: `LivingEntity.createLivingAttributes` RETURN mixin → `builder.add(attribute)`;
    /// Forge: `EntityAttributeModificationEvent` → `event.add(type, attribute)`.
    public static List<EntityAttribute> attributesToAttach() {
        var list = new ArrayList<EntityAttribute>(all.size());
        for (var entry : all) {
            list.add(entry.attribute);
        }
        return list;
    }
}
