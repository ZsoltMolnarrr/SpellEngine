package net.spell_engine.api.loot;

import java.util.HashMap;
import java.util.List;

public class LootConfig {
    public HashMap<String, ItemGroup> item_groups = new HashMap<>();
    public static class ItemGroup { public ItemGroup() { }
        public List<String> ids = List.of();
        public float chance = 1F;
        public float bonus_roll = 0.2F;
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

        public ItemGroup(List<String> ids, int weight) {
            this.ids = ids;
            this.weight = weight;
        }

        public ItemGroup(List<String> ids, float chance, float bonus_roll, int weight) {
            this.ids = ids;
            this.chance = chance;
            this.bonus_roll = bonus_roll;
            this.weight = weight;
        }

        public ItemGroup chance(float chance_multiplier) {
            this.chance = chance_multiplier;
            return this;
        }

        public ItemGroup bonus_roll(float bonus_roll) {
            this.bonus_roll = bonus_roll;
            return this;
        }

        public ItemGroup enchant() {
            this.enchant = new Enchant();
            return this;
        }

        public ItemGroup enchant(int min, int max) {
            this.enchant = new Enchant(min, max);
            return this;
        }
    }
    public HashMap<String, List<String>> loot_tables = new HashMap<>();

    public static LootConfig constrainValues(LootConfig config) {
        if (config.item_groups != null) {
            for (var entry: config.item_groups.entrySet()) {
                var group = entry.getValue();
                if (group.weight < 1) {
                    group.weight = 1;
                }
            }
        }
        return config;
    }
}
