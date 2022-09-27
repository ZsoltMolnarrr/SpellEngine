package net.combatspells.attribute_assigner;

import java.util.Random;

public class ItemAttributeModifier {
    public String attribute;
    public Operation operation = Operation.MULTIPLY;
    public float value = 1;

    public ItemAttributeModifier() {}

    public ItemAttributeModifier(String attribute, float value) {
        this.attribute = attribute;
        this.value = value;
    }

    public enum Operation { ADD, MULTIPLY }
}