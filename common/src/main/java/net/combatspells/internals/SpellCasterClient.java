package net.combatspells.internals;

import net.combatspells.api.spell.Spell;
import net.minecraft.item.ItemStack;

public interface SpellCasterClient {
    Spell getCurrentSpell();
    void setCurrentSpell(Spell spell);

    void castStart(Spell spell);
    void castTick(int remainingUseTicks);
    void castRelease(ItemStack itemStack, int remainingUseTicks);
}
