package net.spell_engine.internals;

import net.spell_engine.api.spell.Spell;
import net.minecraft.util.Identifier;

public interface SpellCasterItemStack {
    Identifier getSpellId();
    Spell getSpell();
}
