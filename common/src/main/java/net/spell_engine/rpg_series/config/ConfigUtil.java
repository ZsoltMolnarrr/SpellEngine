package net.spell_engine.rpg_series.config;

import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.spell_engine.api.item.ItemAttributeModifiers;
import net.spell_engine.utils.AttributeModifierUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ConfigUtil {
    public record Entry(RegistryEntry<EntityAttribute> attribute, EntityAttributeModifier modifier) { }

    public static ItemAttributeModifiers.Builder attributesComponent(Identifier modifierId, List<AttributeModifier> attributesConfig) {
        var builder = ItemAttributeModifiers.builder();
        var modifiers = modifiersFrom(modifierId, attributesConfig);
        for (var modifier : modifiers) {
            builder.add(modifier.attribute(), modifier.modifier(), ItemAttributeModifiers.Slot.ANY);
        }
        return builder;
    }

    public static List<Entry> modifiersFrom(Identifier modifierId, List<AttributeModifier> attributesConfig) {
        var modifiers = new ArrayList<Entry>();
        for (var modifier : attributesConfig) {
            // A blank attribute id is the "no modifier" shape, not a lookup failure — skip it quietly.
            if (!modifier.hasAttribute()) { continue; }
            var attributeId = Identifier.tryParse(modifier.attribute);
            var attribute = attributeId != null
                    ? AttributeModifierUtil.attributeEntry(attributeId)
                    : Optional.<RegistryEntry<EntityAttribute>>empty();
            if (attribute.isPresent()) {
                var id = (modifier.id != null && !modifier.id.isEmpty())
                        ? new Identifier(modifier.id)
                        : modifierId;
                modifiers.add(new Entry(
                        attribute.get(),
                        AttributeModifierUtil.modifier(id, modifier.value, modifier.operation)
                ));
            } else {
                System.err.println("Failed to resolve EntityAttribute with id: `" + modifier.attribute
                        + "` requested by: " + modifierId);
            }
        }
        return modifiers;
    }
}
