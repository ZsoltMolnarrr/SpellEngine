package net.spell_engine.api.config;

import net.fabricmc.loader.api.FabricLoader;

import java.util.List;

public class ShieldConfig {
    public int durability = 150;
    public List<AttributeModifier> attributes = List.of();
    public ConditionalAttributes conditional_attributes = null;
    public List<AttributeModifier> selectedAttributes() {
        if (this.conditional_attributes != null
                && this.conditional_attributes.required_mod() != null
                && FabricLoader.getInstance().isModLoaded(this.conditional_attributes.required_mod())) {
            return this.conditional_attributes.attributes();
        }
        return this.attributes;
    }

    public ShieldConfig() { }
    public ShieldConfig(int durability) {
        this.durability = durability;
    }

    public ShieldConfig attributes(List<AttributeModifier> attributes) {
        this.attributes = attributes;
        return this;
    }

    public ShieldConfig conditionalAttributes(String required_mod, List<AttributeModifier> attributes) {
        this.conditional_attributes = new ConditionalAttributes(required_mod, attributes);
        return this;
    }
}
