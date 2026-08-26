package net.spell_engine.internals.delivery.arrow;

import net.minecraft.core.Holder;
import net.spell_engine.api.spell.Spell;

import java.util.ArrayList;
import java.util.List;

public class ArrowShootContext {
    public static final ArrowShootContext empty() {
        return new ArrowShootContext();
    };

    public boolean firedBySpell = false;
    public List<Holder<Spell>> activeSpells = new ArrayList<>();
}
