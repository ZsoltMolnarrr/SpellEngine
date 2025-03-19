package net.spell_engine.rpg_series.loot;

import net.minecraft.item.Item;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.entry.TagEntry;
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
import net.tinyconfig.ConfigManager;

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
                var id = itemInjectorEntry.id;
                if (id != null && id.startsWith("#")) {
                    // System.out.println("XXX Updating tag: " + id);
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
        LootHelper.TAG_CACHE.save();
    }

    public static void configureV2(RegistryWrapper.WrapperLookup registries, Identifier id, LootTable.Builder tableBuilder, LootConfig config, HashMap<String, Item> entries) {
        var tableId = id.toString();
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


        var attempts = Math.ceil(rolls);
        var chance = pool.rolls / attempts;
        lootPoolBuilder.rolls(BinomialLootNumberProvider.create((int) attempts, (float) chance));
        lootPoolBuilder.bonusRolls(ConstantLootNumberProvider.create(pool.bonus_rolls));
        for (var injectEntry: pool.entries) {
            var entryId = injectEntry.id;
            var weight = injectEntry.weight;
            var enchant = injectEntry.enchant;
            var spellBind = injectEntry.spell_bind;
            if (entryId == null || entryId.isEmpty()) { continue; }

            if (entryId.startsWith("#")) {

                var tagString = entryId.substring(1);

                var itemList = TAG_CACHE.value.cache.get(tagString);
//                    System.out.println("XXX Checking tag: " + entryId + " itemList: " + itemList);
                if (itemList != null && !itemList.isEmpty()) {
//                        System.out.println("XXX Resolving from tag cache: " + tagString);
                    for (var itemId: itemList) {
                        var item = Registries.ITEM.get(Identifier.of(itemId));
                        if (item == null) { continue; }
                        var entry = ItemEntry.builder(item)
                                .weight(weight);
                        if (enchant != null && enchant.isValid()) {
                            var enchantFunction = EnchantWithLevelsLootFunction.builder(registries, numberProvider(enchant.min_power, enchant.max_power));
//                            if (enchant.allow_treasure) {
//                                enchantFunction.allowTreasureEnchantments();
//                            }
                            entry.apply(enchantFunction);
                        }
                        if (spellBind != null && spellBind.isValid()) {
                            // var function = SpellBindRandomlyLootFunction.builder(numberProvider(spellBind.tier_min, spellBind.tier_max));
                            var function = SpellBindRandomlyLootFunction.builder(
                                    spellBind.pool,
                                    numberProvider(spellBind.tier_min, spellBind.tier_max),
                                    numberProvider(spellBind.count_min, spellBind.count_max));
                            entry.apply(function);
                        }
                        lootPoolBuilder.with(entry);
                    }
                } else {
                    var tagId = Identifier.of(tagString);
                    TagKey<Item> tag = TagKey.of(RegistryKeys.ITEM, tagId);

                    if (tag == null) {
                        continue;
                    }
                    var entry = TagEntry.expandBuilder(tag)
                            .weight(weight);

                    if (enchant != null && enchant.isValid()) {
                        var enchantFunction = EnchantWithLevelsLootFunction.builder(registries, numberProvider(enchant.min_power, enchant.max_power));
//                        if (enchant.allow_treasure) {
//                            enchantFunction.allowTreasureEnchantments();
//                        }
                        entry.apply(enchantFunction);
                    }
                    if (spellBind != null && spellBind.isValid()) {
                        // var function = SpellBindRandomlyLootFunction.builder(numberProvider(spellBind.tier_min, spellBind.tier_max));
                        var function = SpellBindRandomlyLootFunction.builder(
                                spellBind.pool,
                                numberProvider(spellBind.tier_min, spellBind.tier_max),
                                numberProvider(spellBind.count_min, spellBind.count_max));
                        entry.apply(function);
                    }
                    lootPoolBuilder.with(entry);
                }
            } else {
                var item = Registries.ITEM.get(Identifier.of(entryId));
                if (item == null) { continue; }
                var entry = ItemEntry.builder(item)
                        .weight(weight);

                if (enchant != null && enchant.isValid()) {
                    var enchantFunction = EnchantWithLevelsLootFunction.builder(registries, numberProvider(enchant.min_power, enchant.max_power));
//                    if (enchant.allow_treasure) {
//                        enchantFunction.allowTreasureEnchantments();
//                    }
                    entry.apply(enchantFunction);
                }
                if (spellBind != null && spellBind.isValid()) {
                    // var function = SpellBindRandomlyLootFunction.builder(numberProvider(spellBind.tier_min, spellBind.tier_max));
                    var function = SpellBindRandomlyLootFunction.builder(
                            spellBind.pool,
                            numberProvider(spellBind.tier_min, spellBind.tier_max),
                            numberProvider(spellBind.count_min, spellBind.count_max));
                    entry.apply(function);
                }
                lootPoolBuilder.with(entry);
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
}
