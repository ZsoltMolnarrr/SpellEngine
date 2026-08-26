package net.spell_engine.internals.delivery.arrow;

import net.minecraft.core.Holder;
import net.spell_engine.api.spell.Spell;

import java.util.List;

public interface ArrowExtension {
    /// `perks` is the resolved result of the spell's own `arrow_perks` merged with any modifier's,
    /// as produced by `ArrowHelper.effectiveArrowPerks` — not necessarily `spellEntry`'s own perks.
    void applyArrowPerks(Holder<Spell> spellEntry, Spell.ArrowPerks perks);
    List<Holder<Spell>> getCarriedSpells();
    boolean isInGround_SpellEngine();
}
