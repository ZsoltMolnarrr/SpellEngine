package net.combatspells.internals;

import net.combatspells.api.spell.Spell;
import net.minecraft.util.Identifier;

public interface SpellCasterEntity {
    Identifier getCurrentSpellId();
    Spell getCurrentSpell();
    float getCurrentCastProgress();
}
