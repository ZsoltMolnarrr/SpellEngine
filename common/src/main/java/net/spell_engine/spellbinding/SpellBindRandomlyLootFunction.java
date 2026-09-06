package net.spell_engine.spellbinding;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameter;
import net.minecraft.loot.function.ConditionalLootFunction;
import net.minecraft.loot.function.LootFunctionType;
import net.minecraft.loot.provider.number.LootNumberProvider;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.item.SpellItemData;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.container.SpellContainer;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.api.spell.container.SpellContainerHelper;
import net.spell_engine.item.ScrollItem;
import net.spell_engine.item.SpellEngineItems;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/// Loot function binding random spells (of a pool / tier) to the item's spell container.
/// JSON: `{"function": "spell_engine:spell_bind_randomly", "pool": "#<tag id>", "tier": <number provider>, "count": <number provider>}`
public class SpellBindRandomlyLootFunction extends ConditionalLootFunction {
    public static final String NAME = "spell_bind_randomly";
    public static final Identifier ID = new Identifier(SpellEngineMod.ID, NAME);
    public static final LootFunctionType TYPE = new LootFunctionType(new Serializer());

    @Nullable private final LootNumberProvider tier;
    @Nullable private final String pool;
    @Nullable private final LootNumberProvider count;

    private SpellBindRandomlyLootFunction(LootCondition[] conditions, @Nullable String pool, @Nullable LootNumberProvider tier, @Nullable LootNumberProvider count) {
        super(conditions);
        this.pool = pool;
        this.tier = tier;
        this.count = count;
    }

    @Override
    public LootFunctionType getType() {
        return TYPE;
    }

    @Override
    public Set<LootContextParameter<?>> getRequiredParameters() {
        // return this.tier.getRequiredParameters();
        return Set.of();
    }

    @Nullable TagKey<Spell> getSpellTag() {
        if (this.pool == null || this.pool.isEmpty()) {
            return null;
        }
        Identifier id;
        if (this.pool.startsWith("#")) {
            id = new Identifier(this.pool.substring(1));
        } else {
            id = new Identifier(this.pool);
        }
        return TagKey.of(SpellRegistry.KEY, id);
    }

    @Override
    public ItemStack process(ItemStack stack, LootContext context) {
        @Nullable final var spellTag = getSpellTag();
        final var selectedTier = this.tier != null ? this.tier.nextInt(context) : -1;
        @Nullable var existingContainer = SpellContainerHelper.containerFromItemStack(stack);
        final List<Identifier> alreadyPresentSpells = existingContainer != null
                ? existingContainer.spell_ids().stream().map(Identifier::new).toList()
                : List.of();
        var spells = SpellRegistry.stream(context.getWorld())
                .filter(entry -> {
                    var id = entry.getKey().get().getValue();
                    return (selectedTier < 0 || entry.value().tier == selectedTier)
                            // && (entry.value().active != null && entry.value().active.scroll != null)
                            && (spellTag == null || entry.isIn(spellTag))
                            && !alreadyPresentSpells.contains(id);
                })
                .toList();

        ArrayList<RegistryEntry<Spell>> selectedSpells = new ArrayList<>();
        if (!spells.isEmpty()) {
            var selectedCount = this.count != null ? this.count.nextInt(context) : 1;
            var retryAttempts = 3;
            for (int i = 0; i < selectedCount; i++) {
                var entry = spells.get(context.getRandom().nextInt(spells.size()));
                while (
                        (retryAttempts > 0) &&
                                // Reroll if
                                // already selected
                                (
                                        selectedSpells.contains(entry)
                                )
                ) {
                    entry = spells.get(context.getRandom().nextInt(spells.size()));
                    retryAttempts -= 1;
                }

                selectedSpells.add(entry);
            }
        }

        if (!selectedSpells.isEmpty()) {
            var newContainer = existingContainer != null ? existingContainer : SpellContainer.EMPTY;
            var newSpellIds = selectedSpells.stream().map(entry -> entry.getKey().get().getValue().toString()).toList();
            newContainer = newContainer
                    .withAdditionalSpell(newSpellIds);
            var sortedSpellIds = SpellContainerHelper.sortedSpells(context.getWorld(), newContainer.spell_ids());
            newContainer = newContainer.copyWith(sortedSpellIds);

            SpellItemData.setSpellContainer(stack, newContainer);

            if (stack.getItem() == SpellEngineItems.SCROLL.get()) {
                var firstSpell = selectedSpells.get(0);
                ScrollItem.onSpellAdded(stack, firstSpell, ScrollItem.resolveSpellPool(context.getWorld(), firstSpell));
            }
        } else {
            if (stack.getItem() == SpellEngineItems.SCROLL.get()) {
                return ItemStack.EMPTY;
            }
        }

        return stack;
    }

    public static ConditionalLootFunction.Builder<?> builder(String pool, LootNumberProvider tier, LootNumberProvider count) {
        return builder(conditions -> new SpellBindRandomlyLootFunction(conditions, pool, tier, count));
    }

    public static class Serializer extends ConditionalLootFunction.Serializer<SpellBindRandomlyLootFunction> {
        @Override
        public void toJson(JsonObject json, SpellBindRandomlyLootFunction function, JsonSerializationContext context) {
            super.toJson(json, function, context);
            if (function.pool != null) {
                json.addProperty("pool", function.pool);
            }
            if (function.tier != null) {
                json.add("tier", context.serialize(function.tier));
            }
            if (function.count != null) {
                json.add("count", context.serialize(function.count));
            }
        }

        @Override
        public SpellBindRandomlyLootFunction fromJson(JsonObject json, JsonDeserializationContext context, LootCondition[] conditions) {
            var pool = JsonHelper.getString(json, "pool", null);
            var tier = json.has("tier") ? JsonHelper.deserialize(json, "tier", context, LootNumberProvider.class) : null;
            var count = json.has("count") ? JsonHelper.deserialize(json, "count", context, LootNumberProvider.class) : null;
            return new SpellBindRandomlyLootFunction(conditions, pool, tier, count);
        }
    }
}
