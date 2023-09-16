package net.spell_engine.api.item;

import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.spell_power.api.MagicSchool;
import net.spell_power.api.attributes.SpellAttributeEntry;
import net.spell_power.api.attributes.SpellAttributes;

import java.util.*;

public class ItemConfig { public ItemConfig() { }
    public static class SpellAttribute {
        public String name;
        public float value;
        public EntityAttributeModifier.Operation operation;

        public SpellAttribute() {}
        public SpellAttribute(String name, float value, EntityAttributeModifier.Operation operation) {
            this.name = name;
            this.value = value;
            this.operation = operation;
        }
        public static SpellAttribute bonus(SpellAttributeEntry attribute, float value) {
            return new SpellAttribute(
                    attribute.name,
                    value,
                    EntityAttributeModifier.Operation.ADDITION
            );
        }

        public static SpellAttribute multiply(SpellAttributeEntry attribute, float value) {
            return new SpellAttribute(
                    attribute.name,
                    value,
                    EntityAttributeModifier.Operation.MULTIPLY_BASE
            );
        }

        public static ArrayList<SpellAttribute> bonuses(EnumSet<MagicSchool> attributes, float value) {
            var list = attributes.stream()
                    .map(school -> SpellAttributes.POWER.get(school))
                    .toList();
            return bonuses(list, value);
        }

        public static ArrayList<SpellAttribute> bonuses(List<SpellAttributeEntry> attributes, float value) {
            ArrayList<SpellAttribute> spellAttributes = new ArrayList<>();
            for (var attribute: attributes) {
                spellAttributes.add(new SpellAttribute(
                        attribute.name,
                        value,
                        EntityAttributeModifier.Operation.ADDITION
                    )
                );
            }
            return spellAttributes;
        }
    }

    public Map<String, Weapon> weapons = new HashMap<>();
    public static class Weapon {
        public float attack_damage = 0;
        public float attack_speed = 0;
        public ArrayList<SpellAttribute> spell_attributes = new ArrayList<>();

        public Weapon() { }
        public Weapon(float attack_damage, float attack_speed) {
            this.attack_damage = attack_damage;
            this.attack_speed = attack_speed;
        }
        public Weapon add(SpellAttribute attribute) {
            spell_attributes.add(attribute);
            return this;
        }
    }

    public Map<String, ArmorSet> armor_sets = new HashMap<>();
    public static class ArmorSet { public ArmorSet() { }
        public float armor_toughness = 0;
        public float knockback_resistance = 0;
        public Piece head = new Piece();
        public Piece chest = new Piece();
        public Piece legs = new Piece();
        public Piece feet = new Piece();
        public static class Piece { public Piece() { }
            public int armor = 0;
            public ArrayList<SpellAttribute> spell_attributes = new ArrayList<>();

            public Piece(int armor) {
                this.armor = armor;
            }

            public Piece add(SpellAttribute attribute) {
                spell_attributes.add(attribute);
                return this;
            }
            public Piece addAll(List<SpellAttribute> attributes) {
                spell_attributes.addAll(attributes);
                return this;
            }
        }

        public static ArmorSet with(Piece head, Piece chest, Piece legs, Piece feet) {
            var set = new ArmorSet();
            set.head = head;
            set.chest = chest;
            set.legs = legs;
            set.feet = feet;
            return set;
        }
    }
}