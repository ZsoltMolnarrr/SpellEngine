package net.spell_engine.api.loot;

import java.util.HashMap;
import java.util.List;

public class LootConfig {
    public HashMap<String, ItemGroup> item_groups = new HashMap<>();
    public static class ItemGroup { public ItemGroup() { }
        public List<String> ids = List.of();
        public float chance = 1F;
        public float bonus_roll_chance = 1.2F;
        public int weight = 1;

        public ItemGroup(List<String> ids, int weight) {
            this.ids = ids;
            this.weight = weight;
        }

        public ItemGroup(List<String> ids, float chance, float bonus_roll_chance, int weight) {
            this.ids = ids;
            this.chance = chance;
            this.bonus_roll_chance = bonus_roll_chance;
            this.weight = weight;
        }

        public ItemGroup chance(float chance_multiplier) {
            this.chance = chance_multiplier;
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
