package net.spell_engine.internals.casting;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.spell_engine.api.spell.Spell;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface SpellCasterClient extends SpellCasterEntity {
    List<Entity> getCurrentTargets();
    Entity getCurrentFirstTarget();


    SpellCast.Attempt startSpellCast(ItemStack itemStack, RegistryEntry<Spell> spellEntry);
    @Nullable SpellCast.Progress getSpellCastProgress();
    boolean isCastingSpell();
    void cancelSpellCast();
}
