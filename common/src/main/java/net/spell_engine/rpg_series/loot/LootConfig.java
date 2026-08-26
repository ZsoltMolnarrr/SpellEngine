package net.spell_engine.rpg_series.loot;

import net.spell_engine.api.tags.SpellTags;
import net.spell_engine.item.ScrollItem;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/// Loot injection config. A loot table is processed with the following priority (first hit wins):
/// 1. `injectors` — exact loot table id
/// 2. `regex_injectors` — regex matched loot table id
/// 3. `fallback` — the table's own contents are inspected; every fallback entry whose `reference`
///    matches an item the table drops gets injected (all of them, independently)
public class LootConfig {
    public LinkedHashMap<String, Pool> injectors = new LinkedHashMap<>();
    public LinkedHashMap<String, Pool> regex_injectors = new LinkedHashMap<>();
    /// Missing from the file (older configs) -> filled with defaults.
    @Nullable public Fallback fallback = null;

    public static class Fallback {
        public static final String DEFAULT_TABLES = "~:chests/";
        /// Global knob: every fallback injected pool's rolls (and bonus rolls) are multiplied by this.
        /// `0` disables fallback injection.
        public float rolls_multiplier = 1F;
        /// Which loot tables fallback injection may apply to (`~regex` or exact id).
        public String tables = DEFAULT_TABLES;
        /// Loot tables excluded from fallback injection (`~regex` or exact id).
        public List<String> blacklist = new ArrayList<>();
        public List<Entry> entries = new ArrayList<>();

        public static class Entry {
            /// Item pattern (`#tag`, `~regex`, or exact id) the loot table must already drop.
            public String reference = "";
            /// Optional per-entry override of `Fallback.tables`.
            @Nullable public String tables = null;
            /// Rolls of the injected pool when the reference gear fills the source pool entirely.
            /// Scaled by the reference's weight share of the source pool: `rolls * share`.
            public float rolls = 1F;
            /// Luck scaling, same semantics as `Pool.bonus_rolls`, scaled like `rolls`.
            public float bonus_rolls = 0.2F;
            public List<Pool.Entry> items = new ArrayList<>();

            public Entry() { }
            public Entry(String reference) {
                this.reference = reference;
            }
            public Entry rolls(double rolls) {
                this.rolls = (float) rolls;
                return this;
            }
            public Entry bonus_rolls(double bonusRolls) {
                this.bonus_rolls = (float) bonusRolls;
                return this;
            }
            public Entry tables(String pattern) {
                this.tables = pattern;
                return this;
            }
            /// Configure the injected items with the `Pool` builder API
            public Entry with(Consumer<Pool> configure) {
                var pool = new Pool();
                pool.entries = this.items;
                configure.accept(pool);
                return this;
            }
        }

        public Fallback add(Entry entry) {
            this.entries.add(entry);
            return this;
        }
    }

    public static class Pool {
        public float rolls = 1F;
        public Pool rolls(double rolls) {
            this.rolls = (float)rolls;
            return this;
        }
        public float bonus_rolls = 0.2F;
        public Pool bonus_rolls(double bonus_roll) {
            this.bonus_rolls = (float)bonus_roll;
            return this;
        }
        @Nullable Boolean skip_conditions = null;
        public Pool skip_conditions() {
            this.skip_conditions = true;
            return this;
        }

        public List<Entry> entries = new ArrayList<>();
        public static class Entry {
            public String id;
            /// If true (default when omitted), filters are combined with OR, else AND
            @Nullable public Boolean filters_lenient = null;
            public boolean filtersLenient() {
                return filters_lenient == null || filters_lenient;
            }
            @Nullable public List<String> filters;
            public Entry(String id) {
                this.id = id;
            }
            public int weight = 1;

            public Enchant enchant = null;
            public static class Enchant { public Enchant() { }
                public float min_power = 1;
                public float max_power = 30;
                public boolean allow_treasure = true;

                public Enchant(float min_power, float max_power) {
                    this.min_power = min_power;
                    this.max_power = max_power;
                }

                public boolean isValid() {
                    return min_power > 0 && max_power > min_power;
                }
            }

            public SpellBind spell_bind = null;
            public static class SpellBind { public SpellBind() { }
                public String pool;
                /// Lowest spell tier that can be bound. `null` (or a negative value, for
                /// backwards compatibility with older config files) means no lower tier limit.
                public Integer tier_min = null;
                /// Highest spell tier that can be bound. `null` (or a negative value, for
                /// backwards compatibility with older config files) means no upper tier limit.
                public Integer tier_max = null;
                public int count_min = 1;
                public int count_max = 1;

                public boolean isValid() {
                    return true;
                }
            }

            public Entry enchant() {
                this.enchant = new Enchant();
                return this;
            }
            public Entry enchant(int min, int max) {
                this.enchant = new Enchant(min, max);
                return this;
            }
        }
        public Pool add(Entry entry) {
            this.entries.add(entry);
            return this;
        }
        public Pool add(String id) {
            return add(id, false);
        }
        public Pool add(String id, int weight) {
            return add(id, false, weight);
        }
        public Pool add(String id, boolean enchant) {
            return add(id, enchant, 0);
        }
        public Pool add(String id, boolean enchant, int weight) {
            Entry entry = new Entry(id);
            if (weight > 0) {
                entry.weight = weight;
            }
            if (enchant) {
                entry.enchant();
            }
            this.entries.add(entry);
            return this;
        }

        public Pool enchant() {
            var entry = this.entries.getLast();
            if (entry != null) {
                entry.enchant();
            }
            return this;
        }

        public Pool scroll(int tier) {
            return scroll(tier, tier);
        }
        public Pool scroll(int min, int max) {
            Entry entry = new Entry(ScrollItem.ID.toString());
            var spell_bind = new Entry.SpellBind();
            spell_bind.pool = SpellTags.TREASURE.location().toString();
            spell_bind.tier_min = min;
            spell_bind.tier_max = max;
            entry.spell_bind = spell_bind;
            this.entries.add(entry);
            return this;
        }

        public Pool bind(String pool, int count_min, int count_max) {
            var entry = this.entries.getLast();
            if (entry != null) {
                var spell_bind = new Entry.SpellBind();
                spell_bind.pool = pool;
                spell_bind.count_min = count_min;
                spell_bind.count_max = count_max;
                entry.spell_bind = spell_bind;
            }
            return this;
        }

        public Pool filter(String... filters) {
            var entry = this.entries.getLast();
            entry.filters = List.of(filters);
            return this;
        }

        public Pool filtersAND() {
            var entry = this.entries.getLast();
            entry.filters_lenient = false;
            return this;
        }

        /// Adjust weight of the last entry
        public Pool weight(int weight) {
            var entry = this.entries.getLast();
            entry.weight = weight;
            return this;
        }

        public Pool modify(Function<Pool, Pool> modifier) {
            return modifier.apply(this);
        }
    }

    public static LootConfig constrainValues(LootConfig config, LootConfig defaults) {
        if (config.injectors == null) { config.injectors = new LinkedHashMap<>(); }
        if (config.regex_injectors == null) { config.regex_injectors = new LinkedHashMap<>(); }
        if (config.fallback == null) {
            config.fallback = defaults.fallback != null ? defaults.fallback : new Fallback();
        }
        var fallback = config.fallback;
        if (fallback.rolls_multiplier < 0) { fallback.rolls_multiplier = 0; }
        if (fallback.tables == null || fallback.tables.isEmpty()) { fallback.tables = Fallback.DEFAULT_TABLES; }
        if (fallback.blacklist == null) { fallback.blacklist = new ArrayList<>(); }
        if (fallback.entries == null) { fallback.entries = new ArrayList<>(); }
        fallback.entries.removeIf(Objects::isNull);
        for (var entry: fallback.entries) {
            if (entry.reference == null) { entry.reference = ""; }
            if (entry.rolls < 0) { entry.rolls = 0; }
            if (entry.bonus_rolls < 0) { entry.bonus_rolls = 0; }
            if (entry.items == null) { entry.items = new ArrayList<>(); }
            constrainEntries(entry.items);
        }
        constrainPools(config.injectors.values());
        constrainPools(config.regex_injectors.values());
        return config;
    }

    private static void constrainEntries(List<Pool.Entry> entries) {
        for (var lootEntry: entries) {
            if (lootEntry.weight < 1) {
                lootEntry.weight = 1;
            }
            // Default value is not serialized (older config files spelled it out)
            if (Boolean.TRUE.equals(lootEntry.filters_lenient)) {
                lootEntry.filters_lenient = null;
            }
        }
    }

    private static void constrainPools(Iterable<? extends Pool> pools) {
        for (var pool: pools) {
            if (pool.entries == null) { pool.entries = new ArrayList<>(); }
            constrainEntries(pool.entries);
        }
    }
}
