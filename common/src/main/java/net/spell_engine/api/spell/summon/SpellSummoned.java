package net.spell_engine.api.spell.summon;

import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.internals.SpellHelper;

public interface SpellSummoned {
    static class Args {
        public final LivingEntity owner;
        public final RegistryEntry<Spell> spell;
        public final SummonBehaviour behaviour;
        public final SpellHelper.ImpactContext context;

        public Args(LivingEntity owner, RegistryEntry<Spell> spell, SummonBehaviour behaviour, SpellHelper.ImpactContext context) {
            this.owner = owner;
            this.spell = spell;
            this.behaviour = behaviour;
            this.context = context;
        }
    }

    void onSummonedBySpell(Args args);
}
