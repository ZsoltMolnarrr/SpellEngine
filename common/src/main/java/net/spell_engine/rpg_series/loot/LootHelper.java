package net.spell_engine.rpg_series.loot;

import net.minecraft.item.Item;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.condition.KilledByPlayerLootCondition;
import net.minecraft.loot.condition.LootConditionTypes;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.function.EnchantWithLevelsLootFunction;
import net.minecraft.loot.provider.number.BinomialLootNumberProvider;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.loot.provider.number.LootNumberProvider;
import net.minecraft.loot.provider.number.UniformLootNumberProvider;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.spell_engine.rpg_series.RPGSeriesCore;
import net.spell_engine.spellbinding.SpellBindRandomlyLootFunction;
import net.tiny_config.ConfigManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class LootHelper {

    public static ConfigManager<TagCache> TAG_CACHE = new ConfigManager<>
            ("tag_cache", new TagCache())
            .builder()
            .setDirectory(RPGSeriesCore.NAMESPACE)
            .sanitize(true)
            .build();

    public static class TagCache {
        public HashMap<String, List<String>> cache = new HashMap<>();
    }

    public static void updateTagCache(LootConfig lootConfig) {
        var updatedTags = new HashSet<String>();
        for (var entry: lootConfig.injectors.entrySet()) {
            var tableId = entry.getKey();
            var pool = entry.getValue();
            for (var itemInjectorEntry: pool.entries) {
                var tagsToCache = new ArrayList<String>();
                if (itemInjectorEntry.id != null) {
                    tagsToCache.add(itemInjectorEntry.id);
                }
                if (itemInjectorEntry.filters != null) {
                    tagsToCache.addAll(itemInjectorEntry.filters);
                }
                for (var id: tagsToCache) {
                    if (id.startsWith("#")) {
                        var tagString = id.substring(1);
                        if (updatedTags.contains(tagString)) {
                            continue;
                        }
                        var tagId = Identifier.of(tagString);
                        TagKey<Item> tag = TagKey.of(RegistryKeys.ITEM, tagId);
                        var itemList = new ArrayList<String>();
                        Registries.ITEM.iterateEntries(tag).forEach((itemEntry) -> {
                            var itemId = itemEntry.getKey().get().getValue();
                            itemList.add(itemId.toString());
                        });
                        LootHelper.TAG_CACHE.value.cache.put(tagString, itemList);
                        updatedTags.add(tagString);
                    }
                }
            }
        }
        LootHelper.TAG_CACHE.save();
    }

    public static void configureV2(RegistryWrapper.WrapperLookup registries, Identifier lootTableId, LootTable.Builder tableBuilder, LootConfig config, HashMap<String, Item> entries) {
        boolean isEntityLootTable = lootTableId.getPath().startsWith("entities");
        var tableId = lootTableId.toString();
        var pool = config.injectors.get(tableId);
        if (pool == null) {
            for (var regex: config.regex_injectors.keySet()) {
                if (tableId.matches(regex)) {
                    pool = config.regex_injectors.get(regex);
                    break;
                }
            }
        }
        if (pool == null) { return; }

        var rolls = pool.rolls > 0 ? pool.rolls : 1F;
        LootPool.Builder lootPoolBuilder = LootPool.builder();
        boolean skipConditions = pool.skip_conditions != null ? pool.skip_conditions : false;
        if (isEntityLootTable && !skipConditions) {
            lootPoolBuilder.conditionally(KilledByPlayerLootCondition.builder());
        }

        var attempts = Math.ceil(rolls);
        var chance = pool.rolls / attempts;
        lootPoolBuilder.rolls(BinomialLootNumberProvider.create((int) attempts, (float) chance));
        lootPoolBuilder.bonusRolls(ConstantLootNumberProvider.create(pool.bonus_rolls));
        for (var entry: pool.entries) {
            var entryId = entry.id;
            var weight = entry.weight;
            var enchant = entry.enchant;
            var spellBind = entry.spell_bind;
            if (entryId == null || entryId.isEmpty()) { continue; }

            // Tag cache is used, because this event handler is called before the game loads the item tags
            List<String> itemList = entryId.startsWith("#")
                            ? TAG_CACHE.value.cache.get(entryId.substring(1))
                            : List.of(entryId);
            List<String> filters = entry.filters != null ? entry.filters : List.of();

            if (itemList == null) {
                System.err.println("RPG Series loot config: failed to resolve itemList for: " + entryId
                + " (Probably just needs a game restart)");
                continue;
            }

            for (var itemId: itemList) {
                var item = Registries.ITEM.get(Identifier.of(itemId));
                if (item == null) { continue; }
                var lootEntry = ItemEntry.builder(item)
                        .weight(weight);
                var filtersMatch = entry.filters_lenient ? filters.isEmpty() : true;
                for (var filter: filters) {
                    if (!filter.startsWith("#")) { continue; }
                    var tag = TAG_CACHE.value.cache.get(filter.substring(1));
                    if (tag == null) { continue; }
                    var contains = tag.contains(itemId);
                    if (entry.filters_lenient) {
                        filtersMatch = filtersMatch || contains;
                    } else {
                        filtersMatch = filtersMatch && contains;
                    }
                }
                if (!filtersMatch) { continue; }

                if (enchant != null && enchant.isValid()) {
                    var enchantFunction = EnchantWithLevelsLootFunction.builder(registries, numberProvider(enchant.min_power, enchant.max_power));
                    lootEntry.apply(enchantFunction);
                }
                if (spellBind != null && spellBind.isValid()) {
                    var function = SpellBindRandomlyLootFunction.builder(
                            spellBind.pool,
                            numberProvider(spellBind.tier_min, spellBind.tier_max),
                            numberProvider(spellBind.count_min, spellBind.count_max));
                    lootEntry.apply(function);
                }
                lootPoolBuilder.with(lootEntry);
            }
        }
        tableBuilder.pool(lootPoolBuilder.build());
    }

    private static LootNumberProvider numberProvider(float min, float max) {
        if (max <= min) {
            return ConstantLootNumberProvider.create(min);
        } else {
            return UniformLootNumberProvider.create(min, max);
        }
    }

    private static List<TagKey<Item>> tagKeys(List<String> tags) {
        return tags.stream().map(LootHelper::tagKey).toList();
    }

    private static TagKey<Item> tagKey(String tag) {
        return TagKey.of(RegistryKeys.ITEM, Identifier.of(tag));
    }
}
