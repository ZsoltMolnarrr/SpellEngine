package net.spell_engine.internals.casting;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface SpellCasterClient extends SpellCasterEntity {
    List<Entity> getCurrentTargets();
    Entity getCurrentFirstTarget();


    SpellCast.Attempt startSpellCast(ItemStack itemStack, Identifier spellId);
    @Nullable SpellCast.Progress getSpellCastProgress();
    boolean isCastingSpell();
    void cancelSpellCast();
}
