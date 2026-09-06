package net.spell_engine.misc.criteria;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.advancement.criterion.AbstractCriterion;
import net.minecraft.advancement.criterion.AbstractCriterionConditions;
import net.minecraft.predicate.entity.AdvancementEntityPredicateDeserializer;
import net.minecraft.predicate.entity.AdvancementEntityPredicateSerializer;
import net.minecraft.predicate.entity.LootContextPredicate;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.utils.PatternMatching;
import org.jetbrains.annotations.Nullable;

/// JSON-based criterion (1.20.1 shape); `spell` / `other_spell` are optional `PatternMatching` patterns.
public class SpellCastCriteria extends AbstractCriterion<SpellCastCriteria.Condition> {
    public static final Identifier ID = new Identifier(SpellEngineMod.ID, "spell_cast");
    public static final SpellCastCriteria INSTANCE = new SpellCastCriteria();
    private static final String SPELL_KEY = "spell";
    private static final String OTHER_SPELL_KEY = "other_spell";

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    protected Condition conditionsFromJson(JsonObject obj, LootContextPredicate playerPredicate, AdvancementEntityPredicateDeserializer predicateDeserializer) {
        return new Condition(playerPredicate, optionalString(obj, SPELL_KEY), optionalString(obj, OTHER_SPELL_KEY));
    }

    @Nullable
    private static String optionalString(JsonObject obj, String key) {
        var element = obj.get(key);
        return (element != null && !element.isJsonNull()) ? element.getAsString() : null;
    }

    public void trigger(ServerPlayerEntity player, RegistryEntry<Spell> spell) {
        trigger(player, condition -> condition.matches(spell));
    }

    public static class Condition extends AbstractCriterionConditions {
        @Nullable private final String spell;
        @Nullable private final String other_spell;

        public Condition(LootContextPredicate player, @Nullable String spell, @Nullable String other_spell) {
            super(ID, player);
            this.spell = spell;
            this.other_spell = other_spell;
        }

        public boolean matches(RegistryEntry<Spell> spellEntry) {
            if (spell == null && other_spell == null) {
                return true;
            }
            if (spell != null && PatternMatching.matches(spellEntry, SpellRegistry.KEY, spell)) {
                return true;
            }
            if (other_spell != null && PatternMatching.matches(spellEntry, SpellRegistry.KEY, other_spell)) {
                return true;
            }
            return false;
        }

        @Override
        public JsonObject toJson(AdvancementEntityPredicateSerializer predicateSerializer) {
            var json = super.toJson(predicateSerializer);
            if (spell != null) {
                json.add(SPELL_KEY, new JsonPrimitive(spell));
            }
            if (other_spell != null) {
                json.add(OTHER_SPELL_KEY, new JsonPrimitive(other_spell));
            }
            return json;
        }
    }
}
