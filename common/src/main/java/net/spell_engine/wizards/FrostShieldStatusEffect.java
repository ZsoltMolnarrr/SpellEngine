package net.spell_engine.wizards;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public class FrostShieldStatusEffect extends StatusEffect {
    public FrostShieldStatusEffect(StatusEffectCategory category, int color) {
        super(category, color);
    }

    public static final Identifier soundId = new Identifier("spell_engine:frost_shield_impact");
    public static final SoundEvent sound = new SoundEvent(soundId);
}
