package net.spell_engine.internals;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.spell_engine.api.spell.SpellContainer;

import java.util.List;

public interface SpellCasterClient extends SpellCasterEntity {
    List<Entity> getCurrentTargets();
    Entity getCurrentFirstTarget();
    void setSelectedSpellIndex(int index);
    void changeSelectedSpellIndex(int delta);
    int getSelectedSpellIndex(SpellContainer container);
    Identifier getSelectedSpellId(SpellContainer container);
    SpellContainer getCurrentContainer();
    void castStart(SpellContainer spell, Hand hand, ItemStack itemStack, int remainingUseTicks);
    void castTick(ItemStack itemStack, int remainingUseTicks);
    void castRelease(ItemStack itemStack, int remainingUseTicks);
}
