package net.spell_engine.internals;

import net.spell_engine.api.spell.Spell;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public interface SpellCasterEntity {
    Identifier getCurrentSpellId();
    Spell getCurrentSpell();
    float getCurrentCastProgress();
    boolean isBeaming();
    @Nullable
    Spell.Release.Target.Beam getBeam();
}
