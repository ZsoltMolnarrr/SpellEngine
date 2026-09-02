package net.spell_engine.spellbinding;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.predicates.ContextAwarePredicate;
import net.minecraft.advancements.predicates.entity.EntityPredicate;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public class SpellBindingCriteria extends SimpleCriterionTrigger<SpellBindingCriteria.Condition> {
    public static final Identifier ID = SpellBinding.ID;
    public static final SpellBindingCriteria INSTANCE = new SpellBindingCriteria();

    @Override
    public Codec<SpellBindingCriteria.Condition> codec() {
        return Condition.CODEC;
    }

    public void trigger(ServerPlayer player, Identifier spellPoolId, boolean isComplete) {
        trigger(player, condition -> {
            return condition.matches(spellPoolId, isComplete);
        });
    }

    public record Condition(Optional<ContextAwarePredicate> player, Optional<String> spell_pool, Optional<Boolean> complete) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<SpellBindingCriteria.Condition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(SpellBindingCriteria.Condition::player),
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


        public Optional<ContextAwarePredicate> player() {
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