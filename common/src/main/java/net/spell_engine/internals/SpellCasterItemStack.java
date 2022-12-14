package net.spell_engine.internals;

import net.spell_engine.api.spell.Spell;
import net.minecraft.util.Identifier;
import net.spell_engine.api.spell.SpellContainer;
import org.jetbrains.annotations.Nullable;

public interface SpellCasterItemStack {
    @Nullable
    SpellContainer getSpellContainer();
}
