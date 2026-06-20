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
        /// Owner-scaled attribute bonuses, applied once at spawn (and re-applied on reload).
        public final AttributeScaling attribute_scaling;
        public final SpellHelper.ImpactContext context;

        public Args(LivingEntity owner, RegistryEntry<Spell> spell, SummonBehaviour behaviour,
                    AttributeScaling attribute_scaling, SpellHelper.ImpactContext context) {
            this.owner = owner;
            this.spell = spell;
            this.behaviour = behaviour;
            this.attribute_scaling = attribute_scaling;
            this.context = context;
        }
    }

    void onSummonedBySpell(Args args);
}
