package net.spell_engine.spellbinding;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancement.criterion.AbstractCriterion;
import net.minecraft.predicate.entity.EntityPredicate;
import net.minecraft.predicate.entity.LootContextPredicate;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.Optional;

public class SpellBindingCriteria extends AbstractCriterion<SpellBindingCriteria.Condition> {
    public static final Identifier ID = SpellBinding.ID;
    public static final SpellBindingCriteria INSTANCE = new SpellBindingCriteria();

    @Override
    public Codec<SpellBindingCriteria.Condition> getConditionsCodec() {
        return Condition.CODEC;
    }

    public void trigger(ServerPlayerEntity player, Identifier spellPoolId, boolean isComplete) {
        trigger(player, condition -> {
            return condition.matches(spellPoolId, isComplete);
        });
    }

    public record Condition(Optional<LootContextPredicate> player, Optional<String> spell_pool, Optional<Boolean> complete) implements AbstractCriterion.Conditions {
        public static final Codec<SpellBindingCriteria.Condition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                EntityPredicate.LOOT_CONTEXT_PREDICATE_CODEC.optionalFieldOf("player").forGetter(SpellBindingCriteria.Condition::player),
                Codec.optionalField("spell_pool", Codec.STRING, true).forGetter(SpellBindingCriteria.Condition::spell_pool),
                Codec.optionalField("complete", Codec.BOOL, true).forGetter(SpellBindingCriteria.Condition::complete)
            )
            .apply(instance, SpellBindingCriteria.Condition::new)
		);

        public boolean matches(Identifier usedSpellPool, boolean isComplete) {
            var poolMatches = true;
            if (spell_pool.isPresent()) {
                poolMatches = spell_pool.get().equals(usedSpellPool.toString());
            }
            if (complete.isPresent()) {
                poolMatches = poolMatches && (complete.get() == isComplete);
            }
            return poolMatches;
        }


        public Optional<LootContextPredicate> player() {
            return this.player;
        }

        public  Optional<String> spell_pool() {
            return this.spell_pool;
        }

        public Optional<Boolean> complete() {
            return this.complete;
        }
    }
}