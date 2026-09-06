package net.spell_engine.misc.criteria;

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
import org.jetbrains.annotations.Nullable;

/// JSON-based criterion (1.20.1 shape); the `enchant_id` condition is optional.
public class EnchantmentSpecificCriteria extends AbstractCriterion<EnchantmentSpecificCriteria.Condition> {
    public static final Identifier ID = new Identifier(SpellEngineMod.ID, "enchant_specific");
    public static final EnchantmentSpecificCriteria INSTANCE = new EnchantmentSpecificCriteria();
    private static final String ENCHANT_ID_KEY = "enchant_id";

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    protected Condition conditionsFromJson(JsonObject obj, LootContextPredicate playerPredicate, AdvancementEntityPredicateDeserializer predicateDeserializer) {
        var element = obj.get(ENCHANT_ID_KEY);
        var enchantId = (element != null && !element.isJsonNull()) ? element.getAsString() : null;
        return new Condition(playerPredicate, enchantId);
    }

    public void trigger(ServerPlayerEntity player, Identifier enchantmentId) {
        trigger(player, condition -> condition.matches(enchantmentId));
    }

    public static class Condition extends AbstractCriterionConditions {
        @Nullable private final String enchant_id;

        public Condition(LootContextPredicate player, @Nullable String enchant_id) {
            super(ID, player);
            this.enchant_id = enchant_id;
        }

        public boolean matches(Identifier id) {
            return enchant_id == null || enchant_id.equals(id.toString());
        }

        @Override
        public JsonObject toJson(AdvancementEntityPredicateSerializer predicateSerializer) {
            var json = super.toJson(predicateSerializer);
            if (enchant_id != null) {
                json.add(ENCHANT_ID_KEY, new JsonPrimitive(enchant_id));
            }
            return json;
        }
    }
}
