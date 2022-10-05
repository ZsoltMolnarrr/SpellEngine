package net.combatspells.internals;

import net.combatspells.api.spell.Spell;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;

public interface SpellCasterClient {
    Spell getCurrentSpell();
    void setCurrentSpell(Spell spell);
    Entity getCurrentTarget();
    float getCurrentCastProgress();
    void castStart(Spell spell);
    void castTick(int remainingUseTicks);
    void castRelease(ItemStack itemStack, int remainingUseTicks);
}
