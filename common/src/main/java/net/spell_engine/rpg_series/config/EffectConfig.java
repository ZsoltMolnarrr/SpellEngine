package net.spell_engine.rpg_series.config;
import net.spell_engine.Platform;

import java.util.List;

public class EffectConfig {
    public List<AttributeModifier> attributes = List.of();
    public ConditionalAttributes conditional_attributes = null;

    public static final EffectConfig EMPTY = new EffectConfig(List.of());

    public EffectConfig() { }
    public EffectConfig(List<AttributeModifier> attributes) {
        this.attributes = attributes;
    }

    public List<AttributeModifier> selectedAttributes() {
        if (this.conditional_attributes != null
                && this.conditional_attributes.required_mod() != null
                && Platform.util().isModLoaded(this.conditional_attributes.required_mod())) {
            return this.conditional_attributes.attributes();
        }
        return this.attributes;
    }

    public List<AttributeModifier> attributes() {
        return this.attributes;
    }

    public AttributeModifier firstModifier() {
        var selected = selectedAttributes();
        if (!selected.isEmpty()) {
            return selected.get(0);
        }
        return AttributeModifier.EMPTY;
    }

    public EffectConfig conditionalAttributes(String required_mod, List<AttributeModifier> attributes) {
        this.conditional_attributes = new ConditionalAttributes(required_mod, attributes);
        return this;
    }
}
