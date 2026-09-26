package net.spell_engine.rpg_series.loot;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.LootTableReporter;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.entry.LeafEntry;
import net.minecraft.loot.entry.LootPoolEntryType;
import net.minecraft.loot.function.LootFunction;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import net.spell_engine.SpellEngineMod;

import java.util.function.Consumer;

/// Rolls an inline loot pool when picked, keeping the item weights within that pool intact.
///
/// 1.20.1 only: stands in for the 1.21 `LootTableEntry` holding an inline `LootTable`
/// (1.20.1 `loot_table` entries can only reference a table by id).
public class InlinePoolEntry extends LeafEntry {
    public static final Identifier ID = new Identifier(SpellEngineMod.ID, "inline_pool");
    public static final LootPoolEntryType TYPE = new LootPoolEntryType(new Serializer());

    private final LootPool pool;

    private InlinePoolEntry(LootPool pool, int weight, int quality, LootCondition[] conditions, LootFunction[] functions) {
        super(weight, quality, conditions, functions);
        this.pool = pool;
    }

    @Override
    public LootPoolEntryType getType() {
        return TYPE;
    }

    @Override
    protected void generateLoot(Consumer<ItemStack> lootConsumer, LootContext context) {
        pool.addGeneratedLoot(lootConsumer, context);
    }

    @Override
    public void validate(LootTableReporter reporter) {
        super.validate(reporter);
        pool.validate(reporter.makeChild(".pool"));
    }

    public static LeafEntry.Builder<?> builder(LootPool pool) {
        return builder((weight, quality, conditions, functions) -> new InlinePoolEntry(pool, weight, quality, conditions, functions));
    }

    public static class Serializer extends LeafEntry.Serializer<InlinePoolEntry> {
        @Override
        public void addEntryFields(JsonObject json, InlinePoolEntry entry, JsonSerializationContext context) {
            super.addEntryFields(json, entry, context);
            json.add("pool", context.serialize(entry.pool));
        }

        @Override
        protected InlinePoolEntry fromJson(JsonObject json, JsonDeserializationContext context, int weight, int quality,
                                           LootCondition[] conditions, LootFunction[] functions) {
            LootPool pool = JsonHelper.deserialize(json, "pool", context, LootPool.class);
            return new InlinePoolEntry(pool, weight, quality, conditions, functions);
        }
    }
}
