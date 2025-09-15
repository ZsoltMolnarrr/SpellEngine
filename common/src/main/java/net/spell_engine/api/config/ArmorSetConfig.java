package net.spell_engine.api.config;

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

        public int armor = 0;
        public ArrayList<AttributeModifier> attributes = new ArrayList<>();

        public Piece(int armor) {
            this.armor = armor;
        }

        public Piece add(AttributeModifier attribute) {
            attributes.add(attribute);
            return this;
        }

        public Piece addAll(List<AttributeModifier> attributes) {
            this.attributes.addAll(attributes);
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
