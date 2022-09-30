package net.combatspells.internals;

import net.combatspells.api.spell.Spell;

public interface SpellCasterEntity {
    Spell getCurrentSpell();
    void setCurrentSpell(Spell spell);
}
