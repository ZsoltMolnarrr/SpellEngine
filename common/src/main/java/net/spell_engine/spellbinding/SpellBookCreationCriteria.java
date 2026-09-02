package net.spell_engine.spellbinding;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.predicates.ContextAwarePredicate;
import net.minecraft.advancements.predicates.entity.EntityPredicate;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.spell_engine.SpellEngineMod;

import java.util.Optional;

public class SpellBookCreationCriteria extends SimpleCriterionTrigger<SpellBookCreationCriteria.Condition> {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(SpellEngineMod.ID, "spell_book_creation");
    public static final SpellBookCreationCriteria INSTANCE = new SpellBookCreationCriteria();

    @Override
    public Codec<SpellBookCreationCriteria.Condition> codec() {
        return SpellBookCreationCriteria.Condition.CODEC;
    }

    public void trigger(ServerPlayer player, Identifier spellPoolId) {
        trigger(player, condition -> {
            return condition.matches(spellPoolId);
        });
    }

    public record Condition(Optional<ContextAwarePredicate> player, Optional<String> spell_pool) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<SpellBookCreationCriteria.Condition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(SpellBookCreationCriteria.Condition::player),
                                Codec.optionalField("spell_pool", Codec.STRING, true).forGetter(SpellBookCreationCriteria.Condition::spell_pool)
                        )
                        .apply(instance, SpellBookCreationCriteria.Condition::new)
        );

        public boolean matches(Identifier id) {
            var poolMatches = true;
            if (spell_pool.isPresent()) {
                poolMatches = spell_pool.get().equals(id.toString());
            }
            return poolMatches;
        }

        public Optional<ContextAwarePredicate> player() {
            return this.player;
        }

        public  Optional<String> spell_pool() {
            return this.spell_pool;
        }
    }
}