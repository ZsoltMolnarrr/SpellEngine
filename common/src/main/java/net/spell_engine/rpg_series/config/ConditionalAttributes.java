package net.spell_engine.rpg_series.config;

import java.util.List;

public record ConditionalAttributes(String required_mod, List<AttributeModifier> attributes) {
}
