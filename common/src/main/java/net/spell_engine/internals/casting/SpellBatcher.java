package net.spell_engine.internals.casting;

import net.minecraft.util.Identifier;

import java.util.Map;

public interface SpellBatcher {
    public class Batch {
        public int trigger_count = 0;
        public Float trigger_chance = null;
        public Boolean cost = null;
    }
    public Map<Identifier, Batch> getSpellBatches();

    default int getBatchTriggerCount(Identifier id) {
        var batch = getSpellBatches().get(id);
        return batch != null ? batch.trigger_count : 0;
    }

    default void batchTriggerCount(Identifier id, int count) {
        var batch = getSpellBatches().getOrDefault(id, new Batch());
        batch.trigger_count = count;
        getSpellBatches().put(id, batch);
    }

    default void batchTriggerChance(Identifier id, float chance) {
        var batch = getSpellBatches().getOrDefault(id, new Batch());
        batch.trigger_chance = chance;
        getSpellBatches().put(id, batch);
    }

    default Float getBatchTriggerChance(Identifier id) {
        var batch = getSpellBatches().get(id);
        return batch != null ? batch.trigger_chance : null;
    }

    default void batchCost(Identifier id, boolean cost) {
        var batch = getSpellBatches().getOrDefault(id, new Batch());
        batch.cost = cost;
        getSpellBatches().put(id, batch);
    }

    default boolean hasBatchedCost(Identifier id) {
        var batch = getSpellBatches().get(id);
        return batch != null && batch.cost != null && batch.cost == true;
    }
}
