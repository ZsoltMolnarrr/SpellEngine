package net.spell_engine.internals.arrow;

import net.minecraft.registry.entry.RegistryEntry;
import net.spell_engine.api.spell.Spell;

import java.util.ArrayList;
import java.util.List;

public class ArrowShootContext {
    public static final ArrowShootContext empty() {
        return new ArrowShootContext();
    };

    public boolean firedBySpell = false;
    public List<RegistryEntry<Spell>> activeSpells = new ArrayList<>();
}
