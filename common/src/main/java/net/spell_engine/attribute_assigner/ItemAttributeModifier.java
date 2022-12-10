package net.spell_engine.attribute_assigner;

import net.minecraft.entity.attribute.EntityAttributeModifier;

public class ItemAttributeModifier {
    public String attribute;
    public EntityAttributeModifier.Operation operation = EntityAttributeModifier.Operation.ADDITION;
    public float value = 1;

    public ItemAttributeModifier() {}

    public ItemAttributeModifier(String attribute, float value) {
        this.attribute = attribute;
        this.value = value;
    }
}