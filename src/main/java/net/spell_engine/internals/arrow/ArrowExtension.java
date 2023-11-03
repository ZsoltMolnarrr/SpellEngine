package net.spell_engine.internals.arrow;

import net.minecraft.util.Identifier;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.SpellInfo;
import org.jetbrains.annotations.Nullable;

public interface ArrowExtension {
    void applyArrowPerks(SpellInfo spellInfo);
    @Nullable Identifier getCarriedSpellId();
    @Nullable Spell getCarriedSpell();
    boolean isInGround_SpellEngine();
    void allowByPassingIFrames_SpellEngine(boolean allow);
}
