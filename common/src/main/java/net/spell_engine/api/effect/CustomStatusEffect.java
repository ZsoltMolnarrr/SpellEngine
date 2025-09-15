package net.spell_engine.api.effect;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

/**
 * Just a plain status effect, that has accessible constructor.
 */
public class CustomStatusEffect extends StatusEffect {
    public CustomStatusEffect(StatusEffectCategory category, int color) {
        super(category, color);
    }
}
