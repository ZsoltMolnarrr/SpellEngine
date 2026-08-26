package net.spell_engine.rpg_series.loot;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.CompositeEntryBase;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.EnchantRandomlyFunction;
import net.minecraft.world.level.storage.loot.functions.EnchantWithLevelsFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemKilledByPlayerCondition;
import net.minecraft.world.level.storage.loot.providers.number.BinomialDistributionGenerator;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.spell_engine.mixin.loot.CombinedEntryAccessor;
import net.spell_engine.mixin.loot.EnchantWithLevelsLootFunctionAccessor;
import net.spell_engine.mixin.loot.ItemEntryAccessor;
import net.spell_engine.mixin.loot.LeafEntryAccessor;
import net.spell_engine.rpg_series.RPGSeriesCore;
import net.spell_engine.rpg_series.tags.RPGSeriesItemTags;
import net.spell_engine.spellbinding.SpellBindRandomlyLootFunction;
import net.spell_engine.utils.PatternMatching;
import net.tiny_config.ConfigManager;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public class LootHelper {

    // MARK: Tag cache

    /// Loot table modification runs before item tags are loaded, so every tag referenced by the
    /// loot config is resolved from this persisted cache (refreshed once tags are available).
    public static ConfigManager<TagCache> TAG_CACHE = new ConfigManager<>
            ("tag_cache", new TagCache())
            .builder()
            .setDirectory(RPGSeriesCore.NAMESPACE)
            .sanitize(true)
            .build();

    public static class TagCache {
        public HashMap<String, List<String>> cache = new HashMap<>();
    }

    private static final String LOOT_TIER_TAG_PREFIX = RPGSeriesCore.NAMESPACE + ":" + RPGSeriesItemTags.LootTiers.FOLDER + "/";
    /// Items of any cached `loot_tier` tag. Tables already dropping these are skipped by the fallback.
    @Nullable private static Set<String> rpgTierItems = null;

    public static void updateTagCache(LootConfig lootConfig) {
        var updatedTags = new HashSet<String>();
        var tagsToCache = new ArrayList<String>();
        var entryLists = new ArrayList<List<LootConfig.Pool.Entry>>();
        lootConfig.injectors.values().forEach(pool -> entryLists.add(pool.entries));
        lootConfig.regex_injectors.values().forEach(pool -> entryLists.add(pool.entries));
        if (lootConfig.fallback != null) {
            for (var fallbackEntry: lootConfig.fallback.entries) {
                entryLists.add(fallbackEntry.items);
                tagsToCache.add(fallbackEntry.reference);
            }
        }
        for (var entries: entryLists) {
            for (var itemInjectorEntry: entries) {
                if (itemInjectorEntry.id != null) {
                    tagsToCache.add(itemInjectorEntry.id);
                }
                if (itemInjectorEntry.filters != null) {
                    tagsToCache.addAll(itemInjectorEntry.filters);
                }
            }
        }
        for (var id: tagsToCache) {
            var pattern = id.startsWith(PatternMatching.NEGATE_PREFIX) ? id.substring(1) : id;
            if (!pattern.startsWith(PatternMatching.TAG_PREFIX)) { continue; }
            var tagString = pattern.substring(1);
            if (updatedTags.contains(tagString)) {
                continue;
            }
            var tagId = Identifier.parse(tagString);
            TagKey<Item> tag = TagKey.create(Registries.ITEM, tagId);
            var itemList = new ArrayList<String>();
            BuiltInRegistries.ITEM.getTagOrEmpty(tag).forEach((itemEntry) -> {
                var itemId = itemEntry.unwrapKey().get().identifier();
                itemList.add(itemId.toString());
            });
            LootHelper.TAG_CACHE.value.cache.put(tagString, itemList);
            updatedTags.add(tagString);
        }
        LootHelper.TAG_CACHE.save();
        rpgTierItems = null;
    }

    private static Set<String> rpgTierItems() {
        if (rpgTierItems == null) {
            var items = new HashSet<String>();
            for (var entry: TAG_CACHE.value.cache.entrySet()) {
                if (entry.getKey().startsWith(LOOT_TIER_TAG_PREFIX)) {
                    items.addAll(entry.getValue());
                }
            }
            rpgTierItems = items;
        }
        return rpgTierItems;
    }

    // MARK: Fallback report

    /// Written after each reload: which tables the fallback injector touched, and why.
    /// Useful for tuning, and as a starting point to promote a table into `injectors`.
    public static ConfigManager<FallbackReport> FALLBACK_REPORT = new ConfigManager<>
            ("loot_fallback_report", new FallbackReport())
            .builder()
            .setDirectory(RPGSeriesCore.NAMESPACE)
            .sanitize(true)
            .build();

    public static class FallbackReport {
        public String info = "Generated by the loot fallback injector, edits have no effect. Key: loot table id -> (config/reference -> decision)";
        public LinkedHashMap<String, LinkedHashMap<String, String>> injected = new LinkedHashMap<>();
        public LinkedHashMap<String, String> skipped = new LinkedHashMap<>();
    }

    private static FallbackReport pendingReport = new FallbackReport();

    public static void saveFallbackReport() {
        FALLBACK_REPORT.value = pendingReport;
        FALLBACK_REPORT.save();
        pendingReport = new FallbackReport();
    }

    // MARK: Injection

    public static void configure(HolderLookup.Provider registries, Identifier lootTableId,
                                 Supplier<List<LootPool>> existingPools, Consumer<LootPool> poolSink,
                                 LootConfig config, String configName) {
        boolean isEntityLootTable = lootTableId.getPath().startsWith("entities");
        var tableId = lootTableId.toString();

        // 1. Exact id
        var pool = config.injectors.get(tableId);
        // 2. Regex id
        if (pool == null) {
            for (var regex: config.regex_injectors.keySet()) {
                if (regexMatches(tableId, regex)) {
                    pool = config.regex_injectors.get(regex);
                    break;
                }
            }
        }
        if (pool != null) {
            boolean skipConditions = pool.skip_conditions != null && pool.skip_conditions;
            poolSink.accept(buildPool(registries, pool.entries, pool.rolls, pool.bonus_rolls,
                    isEntityLootTable && !skipConditions, null));
            return;
        }
        // 3. Fallback, based on what the table already drops
        configureFallback(registries, tableId, existingPools, poolSink, config, configName, isEntityLootTable);
    }

    private static void configureFallback(HolderLookup.Provider registries, String tableId,
                                          Supplier<List<LootPool>> existingPools, Consumer<LootPool> poolSink,
                                          LootConfig config, String configName, boolean isEntityLootTable) {
        var fallback = config.fallback;
        if (fallback == null || fallback.entries.isEmpty() || fallback.rolls_multiplier <= 0) { return; }
        for (var blacklisted: fallback.blacklist) {
            if (matchesId(tableId, blacklisted)) { return; }
        }
        var applicable = new ArrayList<LootConfig.Fallback.Entry>();
        for (var entry: fallback.entries) {
            if (entry.reference.isEmpty()) { continue; }
            var tables = entry.tables != null ? entry.tables : fallback.tables;
            if (matchesId(tableId, tables)) {
                applicable.add(entry);
            }
        }
        if (applicable.isEmpty()) { return; }

        var contents = inspect(existingPools.get());
        if (contents.isEmpty()) { return; }

        var rpgItems = rpgTierItems();
        for (var pool: contents) {
            for (var itemId: pool.items.keySet()) {
                if (rpgItems.contains(itemId)) {
                    pendingReport.skipped.put(tableId, "already drops RPG Series loot: " + itemId);
                    return;
                }
            }
        }

        for (var entry: applicable) {
            var reference = entry.reference;
            float share = 0;
            String matchedItem = null;
            int plainWeight = 0, enchantedWeight = 0;
            Float minLevel = null, maxLevel = null;
            for (var pool: contents) {
                int matchedWeight = 0;
                for (var item: pool.items.entrySet()) {
                    if (!matchesItem(item.getKey(), reference)) { continue; }
                    var occurrence = item.getValue();
                    matchedWeight += occurrence.weight();
                    plainWeight += occurrence.plainWeight;
                    enchantedWeight += occurrence.enchantedWeight;
                    if (occurrence.minLevel != null) {
                        minLevel = minLevel == null ? occurrence.minLevel : Math.min(minLevel, occurrence.minLevel);
                        maxLevel = maxLevel == null ? occurrence.maxLevel : Math.max(maxLevel, occurrence.maxLevel);
                    }
                    if (matchedItem == null) { matchedItem = item.getKey(); }
                }
                if (matchedWeight > 0 && pool.totalWeight > 0) {
                    share = Math.max(share, (float) matchedWeight / (float) pool.totalWeight);
                }
            }
            if (share <= 0) { continue; }

            var scale = share * fallback.rolls_multiplier;
            var rolls = entry.rolls * scale;
            var bonusRolls = entry.bonus_rolls * scale;
            if (rolls <= 0) { continue; }
            var mix = new EnchantMix(plainWeight, enchantedWeight, minLevel, maxLevel);
            poolSink.accept(buildPool(registries, entry.items, rolls, bonusRolls, isEntityLootTable, mix));

            var enchantInfo = enchantedWeight == 0 ? "plain" : plainWeight == 0 ? "enchanted" :
                    String.format(Locale.ROOT, "%.0f%% enchanted", 100F * enchantedWeight / (plainWeight + enchantedWeight));
            if (enchantedWeight > 0 && minLevel != null) {
                enchantInfo += String.format(Locale.ROOT, " (levels %.0f-%.0f)", minLevel, maxLevel);
            }
            pendingReport.injected
                    .computeIfAbsent(tableId, k -> new LinkedHashMap<>())
                    .put(configName + "/" + reference, String.format(Locale.ROOT,
                            "matched %s, share %.2f, rolls %.2f, %s", matchedItem, share, rolls, enchantInfo));
        }
    }

    // MARK: Table inspection

    /// How often an item occurs in a source pool, split by whether the entry enchants it.
    private static class ItemOccurrence {
        int plainWeight = 0;
        int enchantedWeight = 0;
        /// Level range of `enchant_with_levels` functions seen (null if none / not a plain range)
        @Nullable Float minLevel = null;
        @Nullable Float maxLevel = null;
        int weight() { return plainWeight + enchantedWeight; }
    }

    /// Weighted item contents of one source pool, flattened through group/alternative/sequence entries.
    private record PoolContents(LinkedHashMap<String, ItemOccurrence> items, int totalWeight) { }

    private static List<PoolContents> inspect(List<LootPool> pools) {
        var result = new ArrayList<PoolContents>(pools.size());
        for (var pool: pools) {
            var items = new LinkedHashMap<String, ItemOccurrence>();
            int[] total = { 0 };
            for (var entry: pool.entries) {
                collect(entry, items, total);
            }
            if (!items.isEmpty()) {
                result.add(new PoolContents(items, total[0]));
            }
        }
        return result;
    }

    private static void collect(LootPoolEntryContainer entry, LinkedHashMap<String, ItemOccurrence> items, int[] total) {
        if (entry instanceof CompositeEntryBase) {
            for (var child: ((CombinedEntryAccessor) entry).spellEngine_getChildren()) {
                collect(child, items, total);
            }
            return;
        }
        if (!(entry instanceof LootPoolSingletonContainer)) { return; }
        var leaf = (LeafEntryAccessor) entry;
        var weight = leaf.spellEngine_getWeight();
        total[0] += weight;
        if (entry instanceof LootItem) {
            var item = ((ItemEntryAccessor) entry).spellEngine_getItem().value();
            var itemId = BuiltInRegistries.ITEM.getKey(item).toString();
            var occurrence = items.computeIfAbsent(itemId, k -> new ItemOccurrence());
            boolean enchanted = false;
            for (var function: leaf.spellEngine_getFunctions()) {
                if (function instanceof EnchantWithLevelsFunction) {
                    enchanted = true;
                    var levels = ((EnchantWithLevelsLootFunctionAccessor) function).spellEngine_getLevels();
                    Float min = null, max = null;
                    if (levels instanceof ConstantValue constant) {
                        min = constant.value(); max = constant.value();
                    } else if (levels instanceof UniformGenerator uniform
                            && uniform.min() instanceof ConstantValue lo
                            && uniform.max() instanceof ConstantValue hi) {
                        min = lo.value(); max = hi.value();
                    }
                    if (min != null) {
                        occurrence.minLevel = occurrence.minLevel == null ? min : Math.min(occurrence.minLevel, min);
                        occurrence.maxLevel = occurrence.maxLevel == null ? max : Math.max(occurrence.maxLevel, max);
                    }
                } else if (function instanceof EnchantRandomlyFunction) {
                    enchanted = true;
                }
            }
            if (enchanted) {
                occurrence.enchantedWeight += weight;
            } else {
                occurrence.plainWeight += weight;
            }
        }
        // Table references and tag entries are not resolved (referenced tables may not be parsed yet).
    }

    /// Mirrors the enchanted/plain mix of the matched reference gear onto the injected entries:
    /// every injected item is emitted as a plain copy (weight × `plainWeight`) and an enchanted copy
    /// (weight × `enchantedWeight`), so the injected pool enchants as often as the source does.
    private record EnchantMix(int plainWeight, int enchantedWeight, @Nullable Float minLevel, @Nullable Float maxLevel) {
        LootConfig.Pool.Entry.Enchant levels(@Nullable LootConfig.Pool.Entry.Enchant configured) {
            if (configured != null && configured.isValid()) { return configured; }
            var mirrored = new LootConfig.Pool.Entry.Enchant();
            if (minLevel != null && maxLevel != null && maxLevel > minLevel && minLevel > 0) {
                mirrored.min_power = minLevel;
                mirrored.max_power = maxLevel;
            }
            return mirrored;
        }
    }

    // MARK: Pool building

    private static LootPool buildPool(HolderLookup.Provider registries, List<LootConfig.Pool.Entry> entries,
                                      float rolls, float bonusRolls, boolean killedByPlayerOnly, @Nullable EnchantMix mix) {
        LootPool.Builder lootPoolBuilder = LootPool.lootPool();
        if (killedByPlayerOnly) {
            lootPoolBuilder.when(LootItemKilledByPlayerCondition.killedByPlayer());
        }

        rolls = rolls > 0 ? rolls : 1F;
        var attempts = Math.ceil(rolls);
        var chance = rolls / attempts;
        lootPoolBuilder.setRolls(BinomialDistributionGenerator.binomial((int) attempts, (float) chance));
        lootPoolBuilder.setBonusRolls(ConstantValue.exactly(bonusRolls));
        for (var entry: entries) {
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
                var item = BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId));
                if (item == null) { continue; }
                var lootEntry = LootItem.lootTableItem(item)
                        .setWeight(weight);
                var lenient = entry.filtersLenient();
                var filtersMatch = lenient ? filters.isEmpty() : true;
                for (var filter: filters) {
                    if (!filter.startsWith("#")) { continue; }
                    var tag = TAG_CACHE.value.cache.get(filter.substring(1));
                    if (tag == null) { continue; }
                    var contains = tag.contains(itemId);
                    if (lenient) {
                        filtersMatch = filtersMatch || contains;
                    } else {
                        filtersMatch = filtersMatch && contains;
                    }
                }
                if (!filtersMatch) { continue; }

                if (mix != null && spellBind == null) {
                    // Mirror the source: a plain and an enchanted copy, weighted like the reference gear
                    if (mix.plainWeight() > 0) {
                        lootPoolBuilder.add(LootItem.lootTableItem(item).setWeight(weight * mix.plainWeight()));
                    }
                    if (mix.enchantedWeight() > 0) {
                        var levels = mix.levels(enchant);
                        lootPoolBuilder.add(LootItem.lootTableItem(item).setWeight(weight * mix.enchantedWeight())
                                .apply(EnchantWithLevelsFunction.enchantWithLevels(registries, numberProvider(levels.min_power, levels.max_power))));
                    }
                    continue;
                }

                if (enchant != null && enchant.isValid()) {
                    var enchantFunction = EnchantWithLevelsFunction.enchantWithLevels(registries, numberProvider(enchant.min_power, enchant.max_power));
                    lootEntry.apply(enchantFunction);
                }
                if (spellBind != null && spellBind.isValid()) {
                    var function = SpellBindRandomlyLootFunction.builder(
                            spellBind.pool,
                            numberProvider(tierBound(spellBind.tier_min), tierBound(spellBind.tier_max)),
                            numberProvider(spellBind.count_min, spellBind.count_max));
                    lootEntry.apply(function);
                }
                lootPoolBuilder.add(lootEntry);
            }
        }
        return lootPoolBuilder.build();
    }

    // MARK: Pattern matching (tag-cache backed, since tags are not loaded yet)

    private static final HashMap<String, Pattern> REGEX_CACHE = new HashMap<>();

    private static boolean regexMatches(String subject, String regex) {
        var pattern = REGEX_CACHE.computeIfAbsent(regex, r -> Pattern.compile(r, Pattern.CASE_INSENSITIVE));
        return pattern.matcher(subject).find();
    }

    /// Matches a loot table id against a `PatternMatching` style pattern (`~regex`, exact, `!` negation).
    private static boolean matchesId(String id, @Nullable String pattern) {
        if (pattern == null || pattern.isEmpty() || pattern.equals(PatternMatching.ANY)) { return true; }
        if (pattern.startsWith(PatternMatching.NEGATE_PREFIX)) { return !matchesId(id, pattern.substring(1)); }
        if (pattern.startsWith(PatternMatching.REGEX_PREFIX)) { return regexMatches(id, pattern.substring(1)); }
        if (pattern.startsWith(PatternMatching.TAG_PREFIX)) { return false; } // tags are not applicable to table ids
        return id.equals(pattern);
    }

    /// Matches an item id against a `PatternMatching` style pattern; `#tag` resolves through {@link #TAG_CACHE}.
    private static boolean matchesItem(String itemId, @Nullable String pattern) {
        if (pattern == null || pattern.isEmpty() || pattern.equals(PatternMatching.ANY)) { return true; }
        if (pattern.startsWith(PatternMatching.NEGATE_PREFIX)) { return !matchesItem(itemId, pattern.substring(1)); }
        if (pattern.startsWith(PatternMatching.TAG_PREFIX)) {
            var items = TAG_CACHE.value.cache.get(pattern.substring(1));
            return items != null && items.contains(itemId);
        }
        if (pattern.startsWith(PatternMatching.REGEX_PREFIX)) { return regexMatches(itemId, pattern.substring(1)); }
        return itemId.equals(pattern);
    }

    // MARK: Utils

    /// Resolves a nullable tier bound to the value the loot function expects.
    /// `null` — and any negative value, for backwards compatibility with older config
    /// files that used `-1` as the sentinel — both mean "no tier limit", which
    /// {@link net.spell_engine.spellbinding.SpellBindRandomlyLootFunction} reads as a
    /// negative tier (any tier).
    private static float tierBound(Integer tier) {
        return (tier == null || tier < 0) ? -1 : tier;
    }

    private static NumberProvider numberProvider(float min, float max) {
        if (max <= min) {
            return ConstantValue.exactly(min);
        } else {
            return UniformGenerator.between(min, max);
        }
    }
}
