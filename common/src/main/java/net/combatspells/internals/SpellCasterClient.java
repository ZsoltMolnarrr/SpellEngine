package net.combatspells.internals;

import net.combatspells.api.spell.Spell;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;

public interface SpellCasterClient extends SpellCasterEntity {
    Entity getCurrentTarget();
    void castStart(Spell spell);
    void castTick(ItemStack itemStack, int remainingUseTicks);
    void castRelease(ItemStack itemStack, int remainingUseTicks);
}
