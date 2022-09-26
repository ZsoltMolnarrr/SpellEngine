package net.combatspells.attribute_assigner;

import java.util.Random;

public class ItemAttributeModifier {
    public String attribute;
    public Operation operation = Operation.MULTIPLY;
    public Float randomness = null;
    public float value = 1;

    public ItemAttributeModifier() {}

    public ItemAttributeModifier(String attribute, float value) {
        this.attribute = attribute;
        this.value = value;
    }

    private static Random rng = new Random();
    public float randomizedValue() {
        if (randomness == null) {
            return value;
        }
        return (randomness > 0)
                ?  rng.nextFloat(value - randomness, value + randomness)
                : value;
    }

    public enum Operation { ADD, MULTIPLY }
}