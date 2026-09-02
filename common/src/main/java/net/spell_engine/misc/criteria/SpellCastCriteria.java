package net.spell_engine.misc.criteria;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.predicates.ContextAwarePredicate;
import net.minecraft.advancements.predicates.entity.EntityPredicate;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.utils.PatternMatching;

import java.util.Optional;

public class SpellCastCriteria extends SimpleCriterionTrigger<SpellCastCriteria.Condition> {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(SpellEngineMod.ID, "spell_cast");
    public static final SpellCastCriteria INSTANCE = new SpellCastCriteria();

    @Override
    public Codec<Condition> codec() {
        return Condition.CODEC;
    }

    public void trigger(ServerPlayer player, Holder<Spell> spell) {
        trigger(player, condition -> {
            return condition.matches(spell);
        });
    }

    public record Condition(Optional<ContextAwarePredicate> player, Optional<String> spell, Optional<String> other_spell) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<Condition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Condition::player),
                                Codec.optionalField("spell", Codec.STRING, true).forGetter(Condition::spell),
                                Codec.optionalField("other_spell", Codec.STRING, true).forGetter(Condition::other_spell)
                        )
                        .apply(instance, Condition::new)
        );

        public boolean matches(Holder<Spell> spellEntry) {
            if (spell().isEmpty() && other_spell().isEmpty()) {
                return true;
            }
            if (spell().isPresent()) {
                var pattern = spell().get();
                if (PatternMatching.matches(spellEntry, SpellRegistry.KEY, pattern)) {
                    return true;
                }
            }
            if (other_spell().isPresent()) {
                var pattern = other_spell().get();
                if (PatternMatching.matches(spellEntry, SpellRegistry.KEY, pattern)) {
                    return true;
                }
            }
            return false;
        }
    }
}