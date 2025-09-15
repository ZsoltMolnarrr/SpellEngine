package net.spell_engine.spellbinding;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancement.criterion.AbstractCriterion;
import net.minecraft.predicate.entity.EntityPredicate;
import net.minecraft.predicate.entity.LootContextPredicate;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.spell_engine.SpellEngineMod;

import java.util.Optional;

public class SpellBookCreationCriteria extends AbstractCriterion<SpellBookCreationCriteria.Condition> {
    public static final Identifier ID = Identifier.of(SpellEngineMod.ID, "spell_book_creation");
    public static final SpellBookCreationCriteria INSTANCE = new SpellBookCreationCriteria();

    @Override
    public Codec<SpellBookCreationCriteria.Condition> getConditionsCodec() {
        return SpellBookCreationCriteria.Condition.CODEC;
    }

    public void trigger(ServerPlayerEntity player, Identifier spellPoolId) {
        trigger(player, condition -> {
            return condition.matches(spellPoolId);
        });
    }

    public record Condition(Optional<LootContextPredicate> player, Optional<String> spell_pool) implements AbstractCriterion.Conditions {
        public static final Codec<SpellBookCreationCriteria.Condition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                                EntityPredicate.LOOT_CONTEXT_PREDICATE_CODEC.optionalFieldOf("player").forGetter(SpellBookCreationCriteria.Condition::player),
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

        public Optional<LootContextPredicate> player() {
            return this.player;
        }

        public  Optional<String> spell_pool() {
            return this.spell_pool;
        }
    }
}