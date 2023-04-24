package net.spell_engine.internals.criteria;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.advancement.criterion.AbstractCriterion;
import net.minecraft.advancement.criterion.AbstractCriterionConditions;
import net.minecraft.predicate.entity.AdvancementEntityPredicateDeserializer;
import net.minecraft.predicate.entity.AdvancementEntityPredicateSerializer;
import net.minecraft.predicate.entity.EntityPredicate;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.spell_engine.SpellEngineMod;

public class SpellCastCriteria extends AbstractCriterion<SpellCastCriteria.Condition> {
    public static final Identifier ID = new Identifier(SpellEngineMod.ID, "spell_cast");
    public static final SpellCastCriteria INSTANCE = new SpellCastCriteria();
    private static final String spell_pool = "spell_pool";

    @Override
    protected Condition conditionsFromJson(JsonObject obj, EntityPredicate.Extended playerPredicate, AdvancementEntityPredicateDeserializer predicateDeserializer) {
        JsonElement element = obj.get(spell_pool);
        return new Condition(new Identifier(element.getAsString()));
    }

    @Override
    public Identifier getId() {
        return ID;
    }

    public void trigger(ServerPlayerEntity player, Identifier spellPoolId) {
        trigger(player, condition -> condition.test(spellPoolId));
    }

    public static class Condition extends AbstractCriterionConditions {
        Identifier spellPoolId;

        public Condition(Identifier spellPoolId) {
            super(ID, EntityPredicate.Extended.EMPTY);
            this.spellPoolId = spellPoolId;
        }

        public boolean test(Identifier spellPoolId) {
            return this.spellPoolId.equals(spellPoolId);
        }

        @Override
        public JsonObject toJson(AdvancementEntityPredicateSerializer predicateSerializer) {
            JsonObject jsonObject = super.toJson(predicateSerializer);
            jsonObject.add(spell_pool, new JsonPrimitive(spellPoolId.toString()));
            return jsonObject;
        }
    }
}
