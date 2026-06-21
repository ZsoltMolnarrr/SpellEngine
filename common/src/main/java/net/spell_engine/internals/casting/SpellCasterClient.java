package net.spell_engine.internals.casting;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.internals.melee.Melee;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface SpellCasterClient extends SpellCasterEntity {
    List<Entity> getCurrentTargets();
    Entity getCurrentFirstTarget();

    SpellCast.Attempt startSpellCast(ItemStack itemStack, RegistryEntry<Spell> spellEntry);
    @Nullable SpellCast.Progress getSpellCastProgress();
    boolean isCastingSpell();
    void cancelSpellCast();
    /// Releases a CHARGED spell at its current progress (the charge ratio). Below the spell's
    /// `min_release_ratio` the cast fizzles instead.
    void releaseCharge();

    void onAttacksAvailable(List<Melee.Attack> attacks);
    Melee.ActiveAttack getCurrentSkillAttack();
}
