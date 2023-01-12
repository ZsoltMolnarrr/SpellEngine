package net.spell_engine.spellbinding;

import com.google.gson.JsonObject;
import net.minecraft.advancement.criterion.AbstractCriterion;
import net.minecraft.advancement.criterion.AbstractCriterionConditions;
import net.minecraft.predicate.entity.AdvancementEntityPredicateDeserializer;
import net.minecraft.predicate.entity.EntityPredicate;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public class SpellBindingCriteria extends AbstractCriterion<SpellBindingCriteria.Condition> {
    public static final Identifier ID = SpellBinding.ID;
    public static final SpellBindingCriteria INSTANCE = new SpellBindingCriteria();

    @Override
    protected Condition conditionsFromJson(JsonObject obj, EntityPredicate.Extended playerPredicate, AdvancementEntityPredicateDeserializer predicateDeserializer) {
        return new Condition();
    }

    @Override
    public Identifier getId() {
        return SpellBinding.ID;
    }

    public void trigger(ServerPlayerEntity player) {
        trigger(player, condition -> {
            return true;
        });
    }

    public static class Condition extends AbstractCriterionConditions {
        public Condition() {
            super(SpellBinding.ID, EntityPredicate.Extended.EMPTY);
        }
    }
}