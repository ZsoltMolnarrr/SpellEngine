package net.spell_engine.rpg_series.config;
import net.spell_engine.Platform;


import java.util.List;

public class ShieldConfig {
    public int durability = 150;
    public List<AttributeModifier> attributes = List.of();
    public ConditionalAttributes conditional_attributes = null;
    public List<AttributeModifier> selectedAttributes() {
        if (this.conditional_attributes != null
                && this.conditional_attributes.required_mod() != null
                && Platform.util().isModLoaded(this.conditional_attributes.required_mod())) {
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
