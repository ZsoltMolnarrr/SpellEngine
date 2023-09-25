package net.spell_engine.internals.casting;

import net.minecraft.util.Identifier;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.internals.SpellCooldownManager;
import org.jetbrains.annotations.Nullable;

public interface SpellCasterEntity {
    void setCurrentSpellId(Identifier spellId);
    float getCurrentCastProgress();
    SpellCooldownManager getCooldownManager();
    void clearCasting();


    Identifier getCurrentSpellId();
    Spell getCurrentSpell();


    boolean isBeaming();
    @Nullable
    Spell.Release.Target.Beam getBeam();
}
