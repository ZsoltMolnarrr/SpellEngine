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

public class SpellBindingCriteria extends AbstractCriterion<SpellBindingCriteria.Condition> {
    public static final Identifier ID = SpellBinding.ID;
    public static final SpellBindingCriteria INSTANCE = new SpellBindingCriteria();

    @Override
    protected Condition conditionsFromJson(JsonObject obj, LootContextPredicate playerPredicate, AdvancementEntityPredicateDeserializer predicateDeserializer) {
        var condition = new Condition();
        JsonElement element = obj.get("complete");
        if (element != null) {
            condition.complete = element.getAsBoolean();
        }
        element = obj.get("spell_pool");
        if (element != null) {
            condition.spellPool = new Identifier(element.getAsString());
        }
        return condition;
    }

    @Override
    public Identifier getId() {
        return SpellBinding.ID;
    }

    public void trigger(ServerPlayerEntity player, Identifier spellPoolId, boolean isComplete) {
        trigger(player, condition -> {
            return condition.test(spellPoolId, isComplete);
        });
    }

    public static class Condition extends AbstractCriterionConditions {
        // Trigger only when the entire pool has been bound to the item
        boolean complete = false;
        Identifier spellPool = null;
        public Condition() {
            super(SpellBinding.ID, LootContextPredicate.EMPTY);
        }

        public boolean test(Identifier usedSpellPool, boolean isComplete) {
            if (spellPool != null) {
                if (spellPool.equals(usedSpellPool)) {
                    if (this.complete) {
                        // Having to check for completeness
                        return isComplete;
                    }
                }
                return false;
            } else {
                // No conditions, just fire trigger
                return true;
            }
        }

        @Override
        public JsonObject toJson(AdvancementEntityPredicateSerializer predicateSerializer) {
            JsonObject jsonObject = super.toJson(predicateSerializer);
            jsonObject.add("complete", new JsonPrimitive(complete) );
            if (spellPool != null) {
                jsonObject.add("spell_pool", new JsonPrimitive(spellPool.toString()));
            }
            return jsonObject;
        }
    }
}