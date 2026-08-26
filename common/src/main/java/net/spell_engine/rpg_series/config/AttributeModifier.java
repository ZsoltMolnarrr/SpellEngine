package net.spell_engine.rpg_series.config;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;

public class AttributeModifier {
    public AttributeModifier() {
    }

    public static final AttributeModifier EMPTY = new AttributeModifier();

    @Nullable
    public String id;
    public String attribute = "";
    public float value = 0;
    public net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation operation = net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE;

    public AttributeModifier(Identifier attribute, float value, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation operation) {
        this(attribute.toString(), value, operation);
    }
    public AttributeModifier(String attribute, float value, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation operation) {
        this.attribute = attribute;
        this.value = value;
        this.operation = operation;
    }

    public static AttributeModifier bonus(Identifier attributeId, float value) {
        return new AttributeModifier(
                attributeId.toString(),
                value,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE
        );
    }

    public static AttributeModifier multiply(Identifier attributeId, float value) {
        return new AttributeModifier(
                attributeId.toString(),
                value,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_BASE
        );
    }

    public static ArrayList<AttributeModifier> bonuses(List<Identifier> attributeIds, float value) {
        ArrayList<AttributeModifier> spellAttributes = new ArrayList<>();
        for (var attributeId : attributeIds) {
            spellAttributes.add(new AttributeModifier(
                            attributeId.toString(),
                            value,
                            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE
                    )
            );
        }
        return spellAttributes;
    }
}
