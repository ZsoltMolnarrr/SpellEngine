package net.spell_engine.internals.casting;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.spell_engine.api.spell.SpellContainer;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface SpellCasterClient extends SpellCasterEntity {
    boolean isHotbarModifierPressed();
    void setSelectedSpellIndex(int index);
    @Nullable Identifier getSelectedSpellId(SpellContainer container);
    SpellContainer getCurrentContainer();
    SpellContainer.Hosted getCurrentContainerWithHost();
    void castAttempt(SpellCast.Attempt result);
    void castStart(SpellContainer spell, Hand hand, ItemStack itemStack, int remainingUseTicks);
    void castTick(ItemStack itemStack, Hand hand, int remainingUseTicks);
    void castRelease(ItemStack itemStack, Hand hand, int remainingUseTicks);


    List<Entity> getCurrentTargets();
    Entity getCurrentFirstTarget();


    SpellCast.Attempt v2_startSpellCast(ItemStack itemStack, Identifier spellId);
    @Nullable SpellCast.Progress v2_getSpellCastProgress();
    boolean v2_isCastingSpell();
    void v2_cancelSpellCast();
}
