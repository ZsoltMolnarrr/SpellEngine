package net.spell_engine.misc.criteria;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.predicates.ContextAwarePredicate;
import net.minecraft.advancements.predicates.entity.EntityPredicate;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.spell_engine.SpellEngineMod;

import java.util.Optional;

public class EnchantmentSpecificCriteria extends SimpleCriterionTrigger<EnchantmentSpecificCriteria.Condition> {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(SpellEngineMod.ID, "enchant_specific");
    public static final EnchantmentSpecificCriteria INSTANCE = new EnchantmentSpecificCriteria();

    @Override
    public Codec<EnchantmentSpecificCriteria.Condition> codec() {
        return EnchantmentSpecificCriteria.Condition.CODEC;
    }

    public void trigger(ServerPlayer player, Identifier spellPoolId) {
        trigger(player, condition -> {
            return condition.matches(spellPoolId);
        });
    }

    public record Condition(Optional<ContextAwarePredicate> player, Optional<String> enchant_id) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<EnchantmentSpecificCriteria.Condition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(EnchantmentSpecificCriteria.Condition::player),
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

        public Optional<ContextAwarePredicate> player() {
            return this.player;
        }

        public  Optional<String> enchant_id() {
            return this.enchant_id;
        }
    }
}
