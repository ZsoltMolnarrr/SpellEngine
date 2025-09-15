package net.spell_engine.internals.arrow;

import net.minecraft.registry.entry.RegistryEntry;
import net.spell_engine.api.spell.Spell;

import java.util.List;

public interface ArrowExtension {
    void applyArrowPerks(RegistryEntry<Spell> spellEntry);
    List<RegistryEntry<Spell>> getCarriedSpells();
    boolean isInGround_SpellEngine();
}
