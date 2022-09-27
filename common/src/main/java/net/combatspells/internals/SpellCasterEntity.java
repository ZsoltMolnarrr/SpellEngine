package net.combatspells.internals;

import net.combatspells.api.Spell;

public interface SpellCasterEntity {
    Spell getCurrentSpell();
    void setCurrentSpell(Spell spell);
}
