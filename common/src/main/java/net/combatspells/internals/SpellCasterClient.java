package net.combatspells.internals;

import net.combatspells.api.spell.Spell;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;

import java.util.List;

public interface SpellCasterClient extends SpellCasterEntity {
    List<Entity> getCurrentTargets();
    Entity getCurrentFirstTarget();
    void castStart(Spell spell, ItemStack itemStack, int remainingUseTicks);
    void castTick(ItemStack itemStack, int remainingUseTicks);
    void castRelease(ItemStack itemStack, int remainingUseTicks);
}
