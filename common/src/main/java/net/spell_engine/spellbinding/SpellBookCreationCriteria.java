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
import net.spell_engine.SpellEngineMod;

import java.util.Optional;

/// Triggered when a spell book is created at the binding table. JSON: `{"spell_pool": "<pool id>"}` (optional)
public class SpellBookCreationCriteria extends AbstractCriterion<SpellBookCreationCriteria.Condition> {
    public static final Identifier ID = new Identifier(SpellEngineMod.ID, "spell_book_creation");
    public static final SpellBookCreationCriteria INSTANCE = new SpellBookCreationCriteria();

    @Override
    protected Condition conditionsFromJson(JsonObject obj, LootContextPredicate playerPredicate, AdvancementEntityPredicateDeserializer predicateDeserializer) {
        Optional<String> spellPool = Optional.empty();
        JsonElement element = obj.get("spell_pool");
        if (element != null && !element.isJsonNull()) {
            spellPool = Optional.of(element.getAsString());
        }
        return new Condition(playerPredicate, spellPool);
    }

    @Override
    public Identifier getId() {
        return ID;
    }

    public void trigger(ServerPlayerEntity player, Identifier spellPoolId) {
        trigger(player, condition -> {
            return condition.matches(spellPoolId);
        });
    }

    public static class Condition extends AbstractCriterionConditions {
        private final Optional<String> spell_pool;

        public Condition(LootContextPredicate player, Optional<String> spell_pool) {
            super(ID, player);
            this.spell_pool = spell_pool;
        }

        public Condition(Optional<String> spell_pool) {
            this(LootContextPredicate.EMPTY, spell_pool);
        }

        public boolean matches(Identifier id) {
            var poolMatches = true;
            if (spell_pool.isPresent()) {
                poolMatches = id != null && spell_pool.get().equals(id.toString());
            }
            return poolMatches;
        }

        public Optional<String> spell_pool() {
            return this.spell_pool;
        }

        @Override
        public JsonObject toJson(AdvancementEntityPredicateSerializer predicateSerializer) {
            JsonObject jsonObject = super.toJson(predicateSerializer);
            spell_pool.ifPresent(pool -> jsonObject.add("spell_pool", new JsonPrimitive(pool)));
            return jsonObject;
        }
    }
}
