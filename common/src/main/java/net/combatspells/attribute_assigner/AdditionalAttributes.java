package net.combatspells.attribute_assigner;

import java.util.Random;

public class AdditionalAttributes {
    public String attribute;
    public Operation operation = Operation.MULTIPLY;
    public float randomness = 0;
    public float value = 1;

    public AdditionalAttributes() {}

    public AdditionalAttributes(String attribute, float value) {
        this.attribute = attribute;
        this.value = value;
    }

    private static Random rng = new Random();
    public float randomizedValue() {
        return (randomness > 0)
                ?  rng.nextFloat(value - randomness, value + randomness)
                : value;
    }

    public enum Operation { ADD, MULTIPLY }
}