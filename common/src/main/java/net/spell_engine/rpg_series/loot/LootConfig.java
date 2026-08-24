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
/// 3. `fallbacks` — the table's own contents are inspected; every fallback whose `reference`
///    matches an item the table drops gets injected (one per `category`)
public class LootConfig {
    public LinkedHashMap<String, Pool> injectors = new LinkedHashMap<>();
    public LinkedHashMap<String, Pool> regex_injectors = new LinkedHashMap<>();
    /// Ordered: within a `category` the first matching fallback wins, so list higher tiers first.
    /// Missing from the file (older configs) -> filled with defaults; explicit `[]` -> disabled.
    @Nullable public List<Fallback> fallbacks = null;

    public static class Fallback extends Pool {
        public static final String DEFAULT_TABLE_PATTERN = "~:chests/";
        /// Item pattern (`#tag`, `~regex`, or exact id) the loot table must already drop.
        public String reference = "";
        /// Exclusivity group: at most one fallback per category is injected into a table.
        public String category = "";
        /// Which loot tables this fallback may apply to (`~regex` or exact id).
        public String table_pattern = DEFAULT_TABLE_PATTERN;
        /// Added on top of `rolls`, scaled by the reference gear's weight share of the source table
        /// (`effective_rolls = rolls + extra_rolls * share`, share in 0..1).
        public float extra_rolls = 1F;

        public Fallback() {
            this.rolls = 0F;
            this.bonus_rolls = 0F;
        }
        public Fallback(String reference, String category) {
            this();
            this.reference = reference;
            this.category = category;
        }
        public Fallback tables(String pattern) {
            this.table_pattern = pattern;
            return this;
        }
        public Fallback extra_rolls(double extraRolls) {
            this.extra_rolls = (float) extraRolls;
            return this;
        }
        public Fallback with(Consumer<Pool> configure) {
            configure.accept(this);
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
            /// If true, filters combined with OR, else AND
            public boolean filters_lenient = true;
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
            spell_bind.pool = SpellTags.TREASURE.id().toString();
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
        if (config.fallbacks == null) {
            config.fallbacks = defaults.fallbacks != null ? new ArrayList<>(defaults.fallbacks) : new ArrayList<>();
        }
        config.fallbacks.removeIf(Objects::isNull);
        constrainPools(config.injectors.values());
        constrainPools(config.regex_injectors.values());
        constrainPools(config.fallbacks);
        for (var fallback: config.fallbacks) {
            if (fallback.reference == null) { fallback.reference = ""; }
            if (fallback.category == null) { fallback.category = ""; }
            if (fallback.table_pattern == null || fallback.table_pattern.isEmpty()) {
                fallback.table_pattern = Fallback.DEFAULT_TABLE_PATTERN;
            }
            if (fallback.extra_rolls < 0) { fallback.extra_rolls = 0; }
        }
        return config;
    }

    private static void constrainPools(Iterable<? extends Pool> pools) {
        for (var pool: pools) {
            if (pool.entries == null) { pool.entries = new ArrayList<>(); }
            for (var lootEntry: pool.entries) {
                if (lootEntry.weight < 1) {
                    lootEntry.weight = 1;
                }
            }
        }
    }
}
