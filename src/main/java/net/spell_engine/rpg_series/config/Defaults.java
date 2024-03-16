package net.spell_engine.rpg_series.config;

import net.spell_engine.api.loot.LootConfig;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class Defaults {

    public final static LootConfig lootConfig;

    private static String armors(int tier) {
        return "#rpg_series:tier_" + tier + "_armors";
    }
    
    private static String weapons(int tier) {
        return "#rpg_series:tier_" + tier + "_weapons";
    }

    private static String equipment(int tier) {
        return "#rpg_series:tier_" + tier + "_equipment";
    }

    static {
        lootConfig = new LootConfig();
        lootConfig.item_groups.put("weapons_tier_0", new LootConfig.ItemGroup(
                List.of(weapons(0)),
                0.25F,
                0F,
                1
        ).chance(0.3F));

        lootConfig.item_groups.put("weapons_tier_1", new LootConfig.ItemGroup(
                List.of(weapons(1)),
                1
        ).chance(0.3F));
        lootConfig.item_groups.put("weapons_tier_2", new LootConfig.ItemGroup(
                List.of(weapons(2)),
                1
        ).chance(0.3F));
        lootConfig.item_groups.put("weapons_tier_3", new LootConfig.ItemGroup(
                List.of(weapons(2)),
                1
        ).chance(0.3F));
        lootConfig.item_groups.put("weapons_tier_4", new LootConfig.ItemGroup(
                List.of(weapons(2)),
                1
        ).chance(0.3F));
        lootConfig.item_groups.put("weapons_tier_1_enchanted", new LootConfig.ItemGroup(
                List.of(weapons(1)),
                1
        ).chance(0.3F).enchant());
        lootConfig.item_groups.put("weapons_tier_2_enchanted", new LootConfig.ItemGroup(
                List.of(weapons(2)),
                1
        ).chance(0.3F).enchant());

        lootConfig.item_groups.put("armors_tier_1", new LootConfig.ItemGroup(
                List.of(armors(1)),
                1
        ).chance(0.25F));
        lootConfig.item_groups.put("armors_tier_1_enchanted", new LootConfig.ItemGroup(
                List.of(armors(1)),
                1
        ).chance(0.25F).enchant());

        lootConfig.item_groups.put("armors_tier_2", new LootConfig.ItemGroup(
                List.of(armors(2)),
                1
        ).chance(0.5F));
        lootConfig.item_groups.put("armors_tier_2_enchanted", new LootConfig.ItemGroup(
                List.of(armors(2)),
                1
        ).chance(0.5F).enchant());

        List.of("minecraft:chests/abandoned_mineshaft",
                        "minecraft:chests/igloo_chest",
                        "minecraft:chests/shipwreck_supply",
                        "minecraft:chests/jungle_temple")
                .forEach(id -> lootConfig.loot_tables.put(id, List.of("weapons_tier_0")));

        List.of("minecraft:chests/desert_pyramid",
                        "minecraft:chests/bastion_bridge",
                        "minecraft:chests/jungle_temple",
                        "minecraft:chests/pillager_outpost",
                        "minecraft:chests/simple_dungeon",
                        "minecraft:chests/stronghold_crossing")
                .forEach(id -> lootConfig.loot_tables.put(id, List.of("weapons_tier_1")));

        List.of("minecraft:chests/shipwreck_treasure")
                .forEach(id -> lootConfig.loot_tables.put(id, List.of("armors_tier_1")));

        List.of("minecraft:chests/stronghold_library",
                        "minecraft:chests/underwater_ruin_big",
                        "minecraft:chests/woodland_mansion")
                .forEach(id -> lootConfig.loot_tables.put(id, List.of("weapons_tier_1_enchanted", "armors_tier_1_enchanted")));

        List.of("minecraft:chests/bastion_other",
                        "minecraft:chests/nether_bridge",
                        "minecraft:chests/underwater_ruin_small")
                .forEach(id -> lootConfig.loot_tables.put(id, List.of("weapons_tier_2")));

        List.of("minecraft:chests/bastion_treasure",
                        "minecraft:chests/ancient_city",
                        "minecraft:chests/stronghold_library")
                .forEach(id -> lootConfig.loot_tables.put(id, List.of("armors_tier_2_enchanted")));

        List.of("minecraft:chests/end_city_treasure"
                        )
                .forEach(id -> lootConfig.loot_tables.put(id, List.of("weapons_tier_3", "armors_tier_2")));

    }

    @SafeVarargs
    private static <T> List<T> joinLists(List<T>... lists) {
        return Arrays.stream(lists).flatMap(Collection::stream).collect(Collectors.toList());
    }
}
