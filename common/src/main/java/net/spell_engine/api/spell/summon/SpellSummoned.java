package net.spell_engine.api.spell.summon;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.internals.SpellExecution;

public interface SpellSummoned {
    static class Args {
        public final LivingEntity owner;
        public final Holder<Spell> spell;
        public final SummonBehaviour behaviour;
        /// Owner-scaled attribute bonuses, applied once at spawn (and re-applied on reload).
        public final AttributeScaling attribute_scaling;
        public final SpellExecution.ImpactContext context;

        public Args(LivingEntity owner, Holder<Spell> spell, SummonBehaviour behaviour,
                    AttributeScaling attribute_scaling, SpellExecution.ImpactContext context) {
            this.owner = owner;
            this.spell = spell;
            this.behaviour = behaviour;
            this.attribute_scaling = attribute_scaling;
            this.context = context;
        }
    }

    void onSummonedBySpell(Args args);
}
