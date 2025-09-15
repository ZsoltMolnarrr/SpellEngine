package net.spell_engine.api.config;

import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class ConfigUtil {
    public record Entry(RegistryEntry<EntityAttribute> attribute, EntityAttributeModifier modifier) { }
    public static AttributeModifiersComponent.Builder attributesComponent(Identifier modifierId, List<AttributeModifier> attributesConfig) {
        AttributeModifiersComponent.Builder componentBuilder = AttributeModifiersComponent.builder();
        var modifiers = modifiersFrom(modifierId, attributesConfig);
        for (var modifier : modifiers) {
            componentBuilder.add(modifier.attribute(), modifier.modifier(), AttributeModifierSlot.ANY);
        }
        return componentBuilder;
    }

    public static List<Entry> modifiersFrom(Identifier modifierId, List<AttributeModifier> attributesConfig) {
        var modifiers = new ArrayList<Entry>();
        for (var modifier : attributesConfig) {
            var attributeId = Identifier.of(modifier.attribute);
            var attribute = Registries.ATTRIBUTE.getEntry(attributeId);
            if (attribute.isPresent()) {
                var id = (modifier.id != null && !modifier.id.isEmpty())
                        ? Identifier.of(modifier.id)
                        : modifierId;
                modifiers.add(new Entry(
                        attribute.get(),
                        new EntityAttributeModifier(
                                id,
                                modifier.value,
                                modifier.operation
                        )
                ));
            } else {
                System.err.println("Failed to resolve EntityAttribute with id: " + modifier.attribute);
            }
        }
        return modifiers;
    }
}
