package net.spell_engine.api.config;

import net.fabricmc.loader.api.FabricLoader;

import java.util.ArrayList;
import java.util.List;

public class WeaponConfig {
    public float attack_damage = 0;
    public float attack_speed = 0;
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
