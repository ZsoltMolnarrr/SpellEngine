package net.spell_engine.effect;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.spell_engine.api.spell.Spell;
import net.spell_power.api.MagicSchool;
import org.jetbrains.annotations.Nullable;

public class SilenceEffect extends StatusEffect {
    public MagicSchool school;
    public SilenceEffect(StatusEffectCategory statusEffectCategory, int color, @Nullable MagicSchool school) {
        super(statusEffectCategory, color);
        this.school = school;

    }

    public MagicSchool getSchool() {
        return school;
    }
}
