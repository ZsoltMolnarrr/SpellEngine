package net.spell_engine.spellbinding;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.advancement.criterion.AbstractCriterion;
import net.minecraft.advancement.criterion.AbstractCriterionConditions;
import net.minecraft.predicate.entity.AdvancementEntityPredicateDeserializer;
import net.minecraft.predicate.entity.AdvancementEntityPredicateSerializer;
import net.minecraft.predicate.entity.LootContextPredicate;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/// Triggered when a spell is bound at the binding table. JSON: `{"spell_pool": "<pool id>", "complete": true}` (both optional)
public class SpellBindingCriteria extends AbstractCriterion<SpellBindingCriteria.Condition> {
    public static final Identifier ID = SpellBinding.ID;
    public static final SpellBindingCriteria INSTANCE = new SpellBindingCriteria();

    @Override
    protected Condition conditionsFromJson(JsonObject obj, LootContextPredicate playerPredicate, AdvancementEntityPredicateDeserializer predicateDeserializer) {
        Optional<String> spellPool = Optional.empty();
        Optional<Boolean> complete = Optional.empty();
        JsonElement element = obj.get("spell_pool");
        if (element != null && !element.isJsonNull()) {
            spellPool = Optional.of(element.getAsString());
        }
        element = obj.get("complete");
        if (element != null && !element.isJsonNull()) {
            complete = Optional.of(element.getAsBoolean());
        }
        return new Condition(playerPredicate, spellPool, complete);
    }

    @Override
    public Identifier getId() {
        return ID;
    }

    public void trigger(ServerPlayerEntity player, @Nullable Identifier spellPoolId, boolean isComplete) {
        trigger(player, condition -> {
            return condition.matches(spellPoolId, isComplete);
        });
    }

    public static class Condition extends AbstractCriterionConditions {
        private final Optional<String> spell_pool;
        private final Optional<Boolean> complete;

        public Condition(LootContextPredicate player, Optional<String> spell_pool, Optional<Boolean> complete) {
            super(ID, player);
            this.spell_pool = spell_pool;
            this.complete = complete;
        }

        public Condition(Optional<String> spell_pool, Optional<Boolean> complete) {
            this(LootContextPredicate.EMPTY, spell_pool, complete);
        }

        public boolean matches(@Nullable Identifier usedSpellPool, boolean isComplete) {
            var poolMatches = true;
            if (spell_pool.isPresent()) {
                poolMatches = usedSpellPool != null && spell_pool.get().equals(usedSpellPool.toString());
            }
            if (complete.isPresent()) {
                poolMatches = poolMatches && (complete.get() == isComplete);
            }
            return poolMatches;
        }

        public Optional<String> spell_pool() {
            return this.spell_pool;
        }

        public Optional<Boolean> complete() {
            return this.complete;
        }

        @Override
        public JsonObject toJson(AdvancementEntityPredicateSerializer predicateSerializer) {
            JsonObject jsonObject = super.toJson(predicateSerializer);
            spell_pool.ifPresent(pool -> jsonObject.add("spell_pool", new JsonPrimitive(pool)));
            complete.ifPresent(value -> jsonObject.add("complete", new JsonPrimitive(value)));
            return jsonObject;
        }
    }
}
