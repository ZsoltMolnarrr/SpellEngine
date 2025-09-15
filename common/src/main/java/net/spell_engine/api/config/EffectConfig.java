package net.spell_engine.api.config;

import java.util.List;

public record EffectConfig(List<AttributeModifier> attributes) {
    public static EffectConfig EMPTY = new EffectConfig(List.of());
    public AttributeModifier firstModifier() {
        if (!attributes.isEmpty()) {
            return attributes.get(0);
        }
        return AttributeModifier.EMPTY;
    }
}
