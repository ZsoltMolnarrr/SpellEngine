package net.spell_engine.internals.criteria;

import net.minecraft.util.Identifier;

import java.util.List;

public interface SpellCastHistory {
    void saveSpellCast(Identifier spell);
}
