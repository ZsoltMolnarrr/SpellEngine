package net.spell_engine.api.config;

import java.util.ArrayList;

public class WeaponConfig {
    public float attack_damage = 0;
    public float attack_speed = 0;
    public ArrayList<AttributeModifier> attributes = new ArrayList<>();

    public WeaponConfig() {
    }

    public WeaponConfig(float attack_damage, float attack_speed) {
        this.attack_damage = attack_damage;
        this.attack_speed = attack_speed;
    }

    public WeaponConfig add(AttributeModifier attribute) {
        attributes.add(attribute);
        return this;
    }
}
