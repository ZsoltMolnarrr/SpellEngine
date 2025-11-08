package net.spell_engine.api.config;

import java.util.List;

public record ConditionalAttributes(String required_mod, List<AttributeModifier> attributes) {
}
