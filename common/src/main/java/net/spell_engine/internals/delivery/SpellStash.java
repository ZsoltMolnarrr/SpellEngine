package net.spell_engine.internals.delivery;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.spell_engine.api.spell.Spell;

import java.util.List;

public interface SpellStash {
    record Entry(Holder<Spell> spell, List<Spell.Trigger> triggers, Spell.Delivery.StashEffect.ImpactMode impactMode, int consume, boolean delayConsume, boolean consume_any_stacks) { }
    void stashedSpell(Entry spell);
    List<Entry> getStashedSpells();

    static void configure(MobEffect effect, Holder<Spell> spell, List<Spell.Trigger> triggers, Spell.Delivery.StashEffect.ImpactMode impactMode, int consume, boolean delayConsume, boolean consume_any_stacks) {
        ((SpellStash)effect).stashedSpell(new Entry(spell, triggers, impactMode, consume,  delayConsume, consume_any_stacks));
    }

    static List<Entry> getStashedSpells(MobEffect effect) {
        return ((SpellStash)effect).getStashedSpells();
    }
}