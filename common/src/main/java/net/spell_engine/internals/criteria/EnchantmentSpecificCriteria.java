package net.spell_engine.internals.criteria;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancement.criterion.AbstractCriterion;
import net.minecraft.predicate.entity.EntityPredicate;
import net.minecraft.predicate.entity.LootContextPredicate;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.spell_engine.SpellEngineMod;

import java.util.Optional;

public class EnchantmentSpecificCriteria extends AbstractCriterion<EnchantmentSpecificCriteria.Condition> {
    public static final Identifier ID = Identifier.of(SpellEngineMod.ID, "enchant_specific");
    public static final EnchantmentSpecificCriteria INSTANCE = new EnchantmentSpecificCriteria();

    @Override
    public Codec<EnchantmentSpecificCriteria.Condition> getConditionsCodec() {
        return EnchantmentSpecificCriteria.Condition.CODEC;
    }

    public void trigger(ServerPlayerEntity player, Identifier spellPoolId) {
        trigger(player, condition -> {
            return condition.matches(spellPoolId);
        });
    }

    public record Condition(Optional<LootContextPredicate> player, Optional<String> enchant_id) implements AbstractCriterion.Conditions {
        public static final Codec<EnchantmentSpecificCriteria.Condition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                                EntityPredicate.LOOT_CONTEXT_PREDICATE_CODEC.optionalFieldOf("player").forGetter(EnchantmentSpecificCriteria.Condition::player),
                                Codec.optionalField("enchant_id", Codec.STRING, true).forGetter(EnchantmentSpecificCriteria.Condition::enchant_id)
                        )
                        .apply(instance, EnchantmentSpecificCriteria.Condition::new)
        );

        public boolean matches(Identifier id) {
            var poolMatches = true;
            if (enchant_id.isPresent()) {
                poolMatches = enchant_id.get().equals(id.toString());
            }
            return poolMatches;
        }

        public Optional<LootContextPredicate> player() {
            return this.player;
        }

        public  Optional<String> enchant_id() {
            return this.enchant_id;
        }
    }
}
