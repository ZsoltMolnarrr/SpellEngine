package net.spell_engine.rpg_series.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootChoice;
import net.minecraft.loot.LootTableReporter;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.entry.LootPoolEntry;
import net.minecraft.loot.entry.LootPoolEntryType;
import net.minecraft.loot.entry.LootPoolEntryTypes;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.mixin.loot.ItemEntryAccessor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/// Offers all of its children (like vanilla `group`), but shifts weight towards the item entries
/// affiliated with the class of the looting player, see {@link ClassAffiliation}.
///
/// The total weight of the group is preserved, so its share within the pool stays the same.
/// When affiliated, weights are multiplied by {@link #WEIGHT_SCALE} for precision. The affiliation
/// depends on the loot context only, so every group of a pool scales (or not) together -
/// a pool using this entry type should consist of this entry type only.
public class AffiliationGroupEntry extends LootPoolEntry {
    public static final Identifier ID = Identifier.of(SpellEngineMod.ID, "affiliation_group");
    public static final int WEIGHT_SCALE = 100;

    private static final Codec<LootConfig.Behavior.WeightOperation> OPERATION_CODEC = Codec.STRING.xmap(
            name -> LootConfig.Behavior.WeightOperation.valueOf(name.toUpperCase(Locale.ROOT)),
            operation -> operation.name().toLowerCase(Locale.ROOT));

    public static final MapCodec<AffiliationGroupEntry> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                            LootPoolEntryTypes.CODEC.listOf().optionalFieldOf("children", List.of()).forGetter(entry -> entry.children),
                            Codec.FLOAT.optionalFieldOf("extra_weight", 1F).forGetter(entry -> entry.extraWeight),
                            OPERATION_CODEC.optionalFieldOf("operation", LootConfig.Behavior.WeightOperation.MULTIPLY).forGetter(entry -> entry.operation),
                            Codec.BOOL.optionalFieldOf("include_team", true).forGetter(entry -> entry.includeTeam)
                    )
                    .and(addConditionsField(instance).t1())
                    .apply(instance, AffiliationGroupEntry::new)
    );
    public static final LootPoolEntryType TYPE = new LootPoolEntryType(CODEC);

    private final List<LootPoolEntry> children;
    private final float extraWeight;
    private final LootConfig.Behavior.WeightOperation operation;
    private final boolean includeTeam;

    private AffiliationGroupEntry(List<LootPoolEntry> children, float extraWeight, LootConfig.Behavior.WeightOperation operation,
                                  boolean includeTeam, List<LootCondition> conditions) {
        super(conditions);
        this.children = children;
        this.extraWeight = Math.max(extraWeight, 0);
        this.operation = operation;
        this.includeTeam = includeTeam;
    }

    public List<LootPoolEntry> children() {
        return children;
    }

    @Override
    public LootPoolEntryType getType() {
        return TYPE;
    }

    @Override
    public void validate(LootTableReporter reporter) {
        super.validate(reporter);
        if (this.children.isEmpty()) {
            reporter.report("Empty children list");
        }
        for (int i = 0; i < this.children.size(); i++) {
            this.children.get(i).validate(reporter.makeChild(".entry[" + i + "]"));
        }
    }

    @Override
    public boolean expand(LootContext context, Consumer<LootChoice> choiceConsumer) {
        if (!this.test(context)) {
            return false;
        }
        var affiliation = ClassAffiliation.resolve(context, includeTeam);
        if (affiliation.isEmpty()) {
            // Affiliation cannot be determined, configured weights as is
            for (var child: children) {
                child.expand(context, choiceConsumer);
            }
            return true;
        }

        var luck = context.getLuck();
        var choices = new ArrayList<WeightedChoice>();
        float plainTotal = 0;
        float shiftedTotal = 0;
        for (var child: children) {
            var affiliated = isAffiliated(child, affiliation);
            var start = choices.size();
            child.expand(context, choice -> {
                float weight = choice.getWeight(luck);
                var shifted = (affiliated && weight > 0) ? operation.apply(weight, extraWeight) : weight;
                choices.add(new WeightedChoice(choice, weight, shifted));
            });
            for (int i = start; i < choices.size(); i++) {
                var choice = choices.get(i);
                plainTotal += choice.plainWeight;
                shiftedTotal += choice.weight;
            }
        }
        if (shiftedTotal <= 0) {
            return true;
        }
        var normalize = (plainTotal / shiftedTotal) * WEIGHT_SCALE;
        for (var choice: choices) {
            if (choice.weight <= 0) { continue; }
            var weight = Math.max(1, Math.round(choice.weight * normalize));
            choiceConsumer.accept(new LootChoice() {
                @Override
                public int getWeight(float luck) {
                    return weight;
                }
                @Override
                public void generateLoot(Consumer<ItemStack> lootConsumer, LootContext context) {
                    choice.choice.generateLoot(lootConsumer, context);
                }
            });
        }
        return true;
    }

    private record WeightedChoice(LootChoice choice, float plainWeight, float weight) { }

    private static boolean isAffiliated(LootPoolEntry entry, Set<TagKey<Item>> affiliation) {
        if (entry instanceof ItemEntry) {
            var item = ((ItemEntryAccessor) entry).spellEngine_getItem();
            for (var tag: affiliation) {
                if (item.isIn(tag)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static Builder builder(float extraWeight, LootConfig.Behavior.WeightOperation operation, boolean includeTeam) {
        return new Builder(extraWeight, operation, includeTeam);
    }

    public static class Builder extends LootPoolEntry.Builder<Builder> {
        private final List<LootPoolEntry> children = new ArrayList<>();
        private final float extraWeight;
        private final LootConfig.Behavior.WeightOperation operation;
        private final boolean includeTeam;

        private Builder(float extraWeight, LootConfig.Behavior.WeightOperation operation, boolean includeTeam) {
            this.extraWeight = extraWeight;
            this.operation = operation;
            this.includeTeam = includeTeam;
        }

        public Builder with(LootPoolEntry.Builder<?> child) {
            this.children.add(child.build());
            return this;
        }

        public boolean isEmpty() {
            return children.isEmpty();
        }

        @Override
        protected Builder getThisBuilder() {
            return this;
        }

        @Override
        public LootPoolEntry build() {
            return new AffiliationGroupEntry(List.copyOf(children), extraWeight, operation, includeTeam, this.getConditions());
        }
    }
}
