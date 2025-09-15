package net.spell_engine.internals.criteria;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancement.criterion.AbstractCriterion;
import net.minecraft.predicate.entity.EntityPredicate;
import net.minecraft.predicate.entity.LootContextPredicate;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.utils.PatternMatching;

import java.util.Optional;

public class SpellCastCriteria extends AbstractCriterion<SpellCastCriteria.Condition> {
    public static final Identifier ID = Identifier.of(SpellEngineMod.ID, "spell_cast");
    public static final SpellCastCriteria INSTANCE = new SpellCastCriteria();

    @Override
    public Codec<Condition> getConditionsCodec() {
        return Condition.CODEC;
    }

    public void trigger(ServerPlayerEntity player, RegistryEntry<Spell> spell) {
        trigger(player, condition -> {
            return condition.matches(spell);
        });
    }

    public record Condition(Optional<LootContextPredicate> player, Optional<String> spell, Optional<String> other_spell) implements AbstractCriterion.Conditions {
        public static final Codec<Condition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                                EntityPredicate.LOOT_CONTEXT_PREDICATE_CODEC.optionalFieldOf("player").forGetter(Condition::player),
                                Codec.optionalField("spell", Codec.STRING, true).forGetter(Condition::spell),
                                Codec.optionalField("other_spell", Codec.STRING, true).forGetter(Condition::other_spell)
                        )
                        .apply(instance, Condition::new)
        );

        public boolean matches(RegistryEntry<Spell> spellEntry) {
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