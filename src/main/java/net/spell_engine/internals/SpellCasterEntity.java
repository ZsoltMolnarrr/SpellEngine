package net.spell_engine.internals;

import net.minecraft.util.Identifier;
import net.spell_engine.api.spell.Spell;
import org.jetbrains.annotations.Nullable;

public interface SpellCasterEntity {
    void setCurrentSpellId(Identifier spellId);
    Identifier getCurrentSpellId();
    Spell getCurrentSpell();
    float getCurrentCastProgress();
    SpellCooldownManager getCooldownManager();
    void clearCasting();
    boolean isBeaming();
    @Nullable
    Spell.Release.Target.Beam getBeam();

    void v2_castSpell(Identifier spellId);
    void v2_stopCasting();
}
