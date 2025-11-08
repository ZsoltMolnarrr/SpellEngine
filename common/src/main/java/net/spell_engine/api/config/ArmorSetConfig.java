package net.spell_engine.api.config;

import net.fabricmc.loader.api.FabricLoader;

import java.util.ArrayList;
import java.util.List;

public class ArmorSetConfig {
    public ArmorSetConfig() {
    }

    public float armor_toughness = 0;
    public float knockback_resistance = 0;
    public Piece head = new Piece();
    public Piece chest = new Piece();
    public Piece legs = new Piece();
    public Piece feet = new Piece();

    public static class Piece {
        public Piece() {
        }
        public Piece(int armor) {
            this.armor = armor;
        }

        public int armor = 0;
        public ArrayList<AttributeModifier> attributes = new ArrayList<>();
        public ConditionalAttributes conditional_attributes = null;
        public List<AttributeModifier> selectedAttributes() {
            if (this.conditional_attributes != null
                    && this.conditional_attributes.required_mod() != null
                    && FabricLoader.getInstance().isModLoaded(this.conditional_attributes.required_mod())) {
                return this.conditional_attributes.attributes();
            }
            return this.attributes;
        }

        public Piece add(AttributeModifier attribute) {
            attributes.add(attribute);
            return this;
        }

        public Piece addAll(List<AttributeModifier> attributes) {
            this.attributes.addAll(attributes);
            return this;
        }

        public Piece addConditional(String required_mod, List<AttributeModifier> attributes) {
            this.conditional_attributes = new ConditionalAttributes(required_mod, attributes);
            return this;
        }
    }

    public static ArmorSetConfig with(Piece head, Piece chest, Piece legs, Piece feet) {
        var set = new ArmorSetConfig();
        set.head = head;
        set.chest = chest;
        set.legs = legs;
        set.feet = feet;
        return set;
    }
}
