package net.spell_engine.internals.casting;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.spell_engine.api.spell.SpellContainer;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface SpellCasterClient extends SpellCasterEntity {
    void castRelease(ItemStack itemStack, Hand hand, int remainingUseTicks);


    List<Entity> getCurrentTargets();
    Entity getCurrentFirstTarget();


    SpellCast.Attempt v2_startSpellCast(ItemStack itemStack, Identifier spellId);
    @Nullable SpellCast.Progress v2_getSpellCastProgress();
    boolean v2_isCastingSpell();
    void v2_cancelSpellCast();
}
