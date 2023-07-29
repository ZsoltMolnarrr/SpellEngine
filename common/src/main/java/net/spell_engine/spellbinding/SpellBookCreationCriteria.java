package net.spell_engine.spellbinding;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.advancement.criterion.AbstractCriterion;
import net.minecraft.advancement.criterion.AbstractCriterionConditions;
import net.minecraft.predicate.entity.AdvancementEntityPredicateDeserializer;
import net.minecraft.predicate.entity.AdvancementEntityPredicateSerializer;
import net.minecraft.predicate.entity.EntityPredicate;
import net.minecraft.predicate.entity.LootContextPredicate;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.spell_engine.SpellEngineMod;

public class SpellBookCreationCriteria extends AbstractCriterion<SpellBookCreationCriteria.Condition> {
    public static final Identifier ID = new Identifier(SpellEngineMod.ID, "spell_book_creation");
    public static final SpellBookCreationCriteria INSTANCE = new SpellBookCreationCriteria();

    @Override
    protected Condition conditionsFromJson(JsonObject obj, LootContextPredicate playerPredicate, AdvancementEntityPredicateDeserializer predicateDeserializer) {
        var condition = new Condition();
        var element = obj.get("spell_pool");
        if (element != null) {
            condition.spellPool = new Identifier(element.getAsString());
        }
        return condition;
    }

    @Override
    public Identifier getId() {
        return ID;
    }

    public void trigger(ServerPlayerEntity player, Identifier spellPoolId) {
        trigger(player, condition -> {
            return condition.test(spellPoolId);
        });
    }

    public static class Condition extends AbstractCriterionConditions {
        Identifier spellPool = null;
        public Condition() {
            super(ID, LootContextPredicate.EMPTY);
        }

        public boolean test(Identifier usedSpellPool) {
            if (spellPool != null) {
                if (spellPool.equals(usedSpellPool)) {
                    return true;
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
            if (spellPool != null) {
                jsonObject.add("spell_pool", new JsonPrimitive(spellPool.toString()));
            }
            return jsonObject;
        }
    }
}