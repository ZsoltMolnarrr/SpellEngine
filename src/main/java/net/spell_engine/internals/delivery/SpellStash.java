package net.spell_engine.internals.delivery;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.entry.RegistryEntry;
import net.spell_engine.api.spell.Spell;

import java.util.List;

public interface SpellStash {
    record Entry(RegistryEntry<Spell> spell, List<Spell.Trigger> triggers, Spell.Delivery.StashEffect.ImpactMode impactMode, int consume, boolean delayConsume, boolean consume_any_stacks) { }
    void stashedSpell(Entry spell);
    List<Entry> getStashedSpells();

    static void configure(StatusEffect effect, RegistryEntry<Spell> spell, List<Spell.Trigger> triggers, Spell.Delivery.StashEffect.ImpactMode impactMode, int consume, boolean delayConsume, boolean consume_any_stacks) {
        ((SpellStash)effect).stashedSpell(new Entry(spell, triggers, impactMode, consume,  delayConsume, consume_any_stacks));
    }

    static List<Entry> getStashedSpells(StatusEffect effect) {
        return ((SpellStash)effect).getStashedSpells();
    }
}