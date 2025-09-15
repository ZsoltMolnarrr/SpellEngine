package net.spell_engine.api.config;

import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class AttributeModifier {
    public AttributeModifier() {
    }

    public static final AttributeModifier EMPTY = new AttributeModifier();

    @Nullable
    public String id;
    public String attribute = "";
    public float value = 0;
    public EntityAttributeModifier.Operation operation = EntityAttributeModifier.Operation.ADD_VALUE;

    public AttributeModifier(Identifier attribute, float value, EntityAttributeModifier.Operation operation) {
        this(attribute.toString(), value, operation);
    }
    public AttributeModifier(String attribute, float value, EntityAttributeModifier.Operation operation) {
        this.attribute = attribute;
        this.value = value;
        this.operation = operation;
    }

    public static AttributeModifier bonus(Identifier attributeId, float value) {
        return new AttributeModifier(
                attributeId.toString(),
                value,
                EntityAttributeModifier.Operation.ADD_VALUE
        );
    }

    public static AttributeModifier multiply(Identifier attributeId, float value) {
        return new AttributeModifier(
                attributeId.toString(),
                value,
                EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE
        );
    }

    public static ArrayList<AttributeModifier> bonuses(List<Identifier> attributeIds, float value) {
        ArrayList<AttributeModifier> spellAttributes = new ArrayList<>();
        for (var attributeId : attributeIds) {
            spellAttributes.add(new AttributeModifier(
                            attributeId.toString(),
                            value,
                            EntityAttributeModifier.Operation.ADD_VALUE
                    )
            );
        }
        return spellAttributes;
    }
}
