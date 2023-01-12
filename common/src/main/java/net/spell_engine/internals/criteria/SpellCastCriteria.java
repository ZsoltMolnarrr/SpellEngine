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
import net.spell_power.api.MagicSchool;

public class SpellCastCriteria extends AbstractCriterion<SpellCastCriteria.Condition> {
    public static final Identifier ID = new Identifier(SpellEngineMod.ID, "spell_cast");
    public static final SpellCastCriteria INSTANCE = new SpellCastCriteria();
    private static final String magic_school_key = "magic_school";

    @Override
    protected Condition conditionsFromJson(JsonObject obj, EntityPredicate.Extended playerPredicate, AdvancementEntityPredicateDeserializer predicateDeserializer) {
        JsonElement element = obj.get(magic_school_key);
        return new Condition(MagicSchool.valueOf(element.getAsString()));
    }

    @Override
    public Identifier getId() {
        return ID;
    }

    public void trigger(ServerPlayerEntity player, MagicSchool magicSchool) {
        trigger(player, condition -> condition.test(magicSchool));
    }

    public static class Condition extends AbstractCriterionConditions {
        MagicSchool magicSchool;

        public Condition(MagicSchool magicSchool) {
            super(ID, EntityPredicate.Extended.EMPTY);
            this.magicSchool = magicSchool;
        }

        public boolean test(MagicSchool magicSchool) {
            return this.magicSchool == magicSchool;
        }

        @Override
        public JsonObject toJson(AdvancementEntityPredicateSerializer predicateSerializer) {
            JsonObject jsonObject = super.toJson(predicateSerializer);
            jsonObject.add(magic_school_key, new JsonPrimitive(magicSchool.toString()));
            return jsonObject;
        }
    }
}
