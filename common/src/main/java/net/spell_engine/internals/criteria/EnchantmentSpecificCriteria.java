package net.spell_engine.internals.criteria;

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

public class EnchantmentSpecificCriteria extends AbstractCriterion<EnchantmentSpecificCriteria.Condition> {
    public static final Identifier ID = new Identifier(SpellEngineMod.ID, "enchant_specific");
    public static final EnchantmentSpecificCriteria INSTANCE = new EnchantmentSpecificCriteria();
    private static final String enchant_id_key = "enchant_id";

    @Override
    protected Condition conditionsFromJson(JsonObject obj, LootContextPredicate playerPredicate, AdvancementEntityPredicateDeserializer predicateDeserializer) {
        JsonElement element = obj.get(enchant_id_key);
        return new EnchantmentSpecificCriteria.Condition(element.getAsString());
    }

    @Override
    public Identifier getId() {
        return ID;
    }

    public void trigger(ServerPlayerEntity player, String enchantId) {
        trigger(player, condition -> condition.test(enchantId));
    }

    public static class Condition extends AbstractCriterionConditions {
        String enchantId;

        public Condition(String enchantId) {
            super(ID, LootContextPredicate.EMPTY);
            this.enchantId = enchantId;
        }

        public boolean test(String enchantId) {
            return this.enchantId.equals(enchantId);
        }

        @Override
        public JsonObject toJson(AdvancementEntityPredicateSerializer predicateSerializer) {
            JsonObject jsonObject = super.toJson(predicateSerializer);
            jsonObject.add(enchant_id_key, new JsonPrimitive(enchantId));
            return jsonObject;
        }
    }
}
