package net.spell_engine.internals.criteria;

import net.minecraft.util.Identifier;
import net.spell_power.api.MagicSchool;

public interface SpellCastHistory {
    void saveSpellCast(MagicSchool magicSchool, Identifier id);
    boolean hasCastedAllOf(MagicSchool magicSchool);
}
