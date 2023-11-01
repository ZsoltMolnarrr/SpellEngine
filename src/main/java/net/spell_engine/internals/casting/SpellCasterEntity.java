package net.spell_engine.internals.casting;

import net.minecraft.util.Identifier;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.internals.SpellCooldownManager;
import org.jetbrains.annotations.Nullable;

public interface SpellCasterEntity {
    SpellCooldownManager getCooldownManager();

    void setSpellCastProcess(@Nullable SpellCast.Process process);
    @Nullable SpellCast.Process getSpellCastProcess();

    Spell getCurrentSpell(); // Used by Better Combat compatibility
    float getCurrentCastingSpeed();

    boolean isBeaming();
    @Nullable
    Spell.Release.Target.Beam getBeam();
}
