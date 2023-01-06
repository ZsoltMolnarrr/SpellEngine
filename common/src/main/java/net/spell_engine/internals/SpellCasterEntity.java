package net.spell_engine.internals;

import net.spell_engine.api.spell.Spell;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public interface SpellCasterEntity {
    void setCurrentSpell(Identifier spellId);
    Identifier getCurrentSpellId();
    Spell getCurrentSpell();
    float getCurrentCastProgress();
    SpellCooldownManager getCooldownManager();
    void clearCasting();
    boolean isBeaming();
    @Nullable
    Spell.Release.Target.Beam getBeam();
}
