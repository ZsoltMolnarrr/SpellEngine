package net.spell_engine.rpg_series.config;

import net.spell_engine.rpg_series.loot.LootConfig;
import net.spell_engine.rpg_series.tags.RPGSeriesItemTags;

import java.util.*;

public class Defaults {

    public final static LootConfig itemLootConfig;
    public final static LootConfig scrollLootConfig;

    private static String armors(int tier) {
        return "#" + RPGSeriesItemTags.LootTiers.id(tier, RPGSeriesItemTags.LootCategory.ARMORS).toString();
    }
    
    private static String weapons(int tier) {
        return "#" + RPGSeriesItemTags.LootTiers.id(tier, RPGSeriesItemTags.LootCategory.WEAPONS).toString();
    }

    private static String relics(int tier) {
        return "#" + RPGSeriesItemTags.LootTiers.id(tier, RPGSeriesItemTags.LootCategory.RELICS).toString();
    }

    private static String accessories(int tier) {
        return "#" + RPGSeriesItemTags.LootTiers.id(tier, RPGSeriesItemTags.LootCategory.ACCESSORIES).toString();
    }

    static {
        var GOLDEN = "#" + RPGSeriesItemTags.LootThemes.id(RPGSeriesItemTags.LootTheme.GOLDEN_WEAPON).toString();
        var AETHER = "#" + RPGSeriesItemTags.LootThemes.id(RPGSeriesItemTags.LootTheme.AETHER).toString();
        var DRAGON = "#" + RPGSeriesItemTags.LootThemes.id(RPGSeriesItemTags.LootTheme.DRAGON).toString();

        var W0 = weapons(0);
        var W1 = weapons(1);
        var W2 = weapons(2);
        var W3 = weapons(3);
        var W4 = weapons(4);
        var W5 = weapons(5);

        var A1 = armors(1);
        var A2 = armors(2);
        var A3 = armors(3);

        var X0 = accessories(0);
        var X1 = accessories(1);
        var X2 = accessories(2);
        var X3 = accessories(3);
        var X4 = accessories(4);

        var R1 = relics(1);
        var R2 = relics(2);
        var R3 = relics(3);
        var R4 = relics(4);

        itemLootConfig = new LootConfig();
        var items = itemLootConfig.injectors;
        var items_regex = itemLootConfig.regex_injectors;

        scrollLootConfig = new LootConfig();
        var scrolls = scrollLootConfig.injectors;
        var scrolls_regex = scrollLootConfig.regex_injectors;

        var arsenal_heal_spells = "#arsenal:heal";
        var arsenal_melee_spells = "#arsenal:melee";
        var arsenal_ranged_spells = "#arsenal:ranged";
        var arsenal_shield_spells = "#arsenal:shield";
        var arsenal_spell_spells = "#arsenal:spell";

        // Vanilla loot table items

        items.put("minecraft:chests/ruined_portal", new LootConfig.Pool()
                .rolls(2)
                .add(GOLDEN)
                .add(GOLDEN).enchant()
        );

        List.of("minecraft:chests/abandoned_mineshaft",
                "minecraft:chests/igloo_chest",
                "minecraft:chests/shipwreck_supply",
                "minecraft:chests/spawn_bonus_chest").
                forEach(id ->
                        items.put(id, new LootConfig.Pool()
                        .rolls(0.5)
                        .add(W0)
                        .add(X0)
                ));

        List.of("minecraft:chests/bastion_bridge",
                "minecraft:chests/simple_dungeon",
                "minecraft:chests/stronghold_corridor",
                "minecraft:chests/stronghold_crossing",
                "minecraft:chests/buried_treasure")
                .forEach(id -> {
                    items.put(id, new LootConfig.Pool()
                            .rolls(0.5)
                            .add(W1)
                            .add(X2));
                    scrolls.put(id, new LootConfig.Pool()
                            .rolls(0.2)
                            .scroll(2, 3));
                });

        List.of("minecraft:chests/shipwreck_treasure")
                .forEach(id -> items.put(id, new LootConfig.Pool()
                        .rolls(0.5)
                        .add(A1)
                ));

        List.of("minecraft:chests/desert_pyramid",
                "minecraft:chests/underwater_ruin_small",
                "minecraft:chests/jungle_temple",
                "minecraft:chests/pillager_outpost",
                "minecraft:chests/woodland_mansion")
                .forEach(id -> {
                    items.put(id, new LootConfig.Pool()
                            .rolls(1)
                            .add(W1, true).weight(2)
                            .add(A1, true).weight(2)
                            .add(X1, true).weight(2)
                            .add(R1)
                    );
                    scrolls.put(id, new LootConfig.Pool()
                            .rolls(0.5)
                            .scroll(1, 2)
                    );
                });

        List.of("minecraft:chests/nether_bridge",
                    "minecraft:chests/underwater_ruin_big")
                .forEach(id -> {
                    items.put(id, new LootConfig.Pool()
                        .rolls(0.75)
                        .add(W2)
                        .add(X2));
                    scrolls.put(id, new LootConfig.Pool()
                            .rolls(0.2)
                            .scroll(2, 3));
                });

        List.of("minecraft:chests/bastion_other")
                .forEach(id -> items.put(id, new LootConfig.Pool()
                        .rolls(0.5)
                        .add(W1, true)
                        .add(X3)
                ));

        items.put("minecraft:chests/bastion_treasure", new LootConfig.Pool()
                .rolls(2)
                .add(A2).enchant()
                .add(W3).enchant()
                .add(X3)
                .add(R2)
        );
        scrolls.put("minecraft:chests/bastion_treasure", new LootConfig.Pool()
                .rolls(0.5)
                .scroll(3, 4)
        );

        List.of("minecraft:chests/ancient_city")
                .forEach(id -> items.put(id, new LootConfig.Pool()
                        .rolls(0.8)
                        .add(A2, true)
                        .add(X2, false)
                ));

        List.of("minecraft:chests/stronghold_library")
                .forEach(id -> {
                    items.put(id, new LootConfig.Pool()
                        .rolls(0.8)
                        .add(A2, true)
                        .add(X2, false)
                    );
                    scrolls.put(id, new LootConfig.Pool()
                        .scroll(3, 4)
                    );
                });

        List.of("minecraft:chests/end_city_treasure")
                .forEach(id -> items.put(id, new LootConfig.Pool()
                        .rolls(1)
                        .bonus_rolls(0)
                        .add(W4, true)
                        .add(A3, true)
                        .add(X4)
                        .add(R4)
                        .modify(pool -> addWithArsenalSpellBinding(pool, W5, 1))
                ));

        List.of("minecraft:chests/trial_chambers/corridor",
                        "minecraft:chests/trial_chambers/entrance")
                .forEach(id -> items.put(id, new LootConfig.Pool()
                        .rolls(0.5)
                        .add(W0)
                ));

        List.of("minecraft:chests/trial_chambers/reward_ominous_common",
                        "minecraft:chests/trial_chambers/reward_common")
                .forEach(id -> {
                    items.put(id, new LootConfig.Pool()
                        .rolls(0.5)
                        .add(W1).weight(2)
                        .add(A1).weight(2)
                        .add(R1)
                    );
                    scrolls.put(id, new LootConfig.Pool()
                        .rolls(0.5)
                        .scroll(1, 2)
                    );
                });

        List.of("minecraft:chests/trial_chambers/reward_ominous_rare",
                        "minecraft:chests/trial_chambers/reward_rare")
                .forEach(id -> {
                    items.put(id, new LootConfig.Pool()
                        .rolls(1)
                        .add(W2, true)
                        .add(A2, true)
                        .add(X2)
                        .add(R2)
                    );
                    scrolls.put(id, new LootConfig.Pool()
                        .rolls(0.5)
                        .scroll(2, 3)
                    );
                });

        List.of("minecraft:chests/trial_chambers/reward_ominous_unique",
                        "minecraft:chests/trial_chambers/reward_unique")
                .forEach(id -> {
                    items.put(id, new LootConfig.Pool()
                        .rolls(1)
                        .add(W3, true)
                        .add(X4)
                        .add(R3)
                    );
                });

        // BOSSES

        // Vanilla demi bosses

        items.put("minecraft:entities/evoker", new LootConfig.Pool()
                .rolls(0.25F)
                .add(R1)
        );

        items.put("minecraft:entities/illusioner", new LootConfig.Pool()
                .rolls(0.25F)
                .add(R1)
        );

        // Vanilla large bosses

        items.put("minecraft:entities/ender_dragon", new LootConfig.Pool()
                .rolls(3)
                .add(W5)
                .add(W3).enchant()
                .add(X4)
                .add(R3).filter(DRAGON)
        );

        items.put("minecraft:entities/wither", new LootConfig.Pool()
                .rolls(2)
                .add(W3, true)
                .add(A3, true)
                .add(X3)
                .add(R2)
                .add(W5)
        );

        items.put("minecraft:entities/warden", new LootConfig.Pool()
                .rolls(2)
                .add(W2, true)
                .add(A2, true)
                .add(X2)
                .add(R2)
                .add(W5)
        );

        // MineCells bosses

        items.put("minecells:entities/conjunctivius", new LootConfig.Pool()
                .rolls(2)
                .add(W2, true)
                .add(A2, true)
                .add(X4)
                .add(R2)
                .add(W5)
        );

        items.put("minecells:entities/concierge", new LootConfig.Pool()
                .rolls(2)
                .add(W2, true)
                .add(A2, true)
                .add(X4)
                .add(R3)
                .modify(pool -> addWithArsenalSpellBinding(pool, W5, 1))
        );

        // Bosses of Mass Destruction mod

        items.put("bosses_of_mass_destruction:entities/lich", new LootConfig.Pool()
                .rolls(2)
                .add(W2, true)
                .add(A2, true)
                .add(X2)
                .add(R2)
        );

        items.put("bosses_of_mass_destruction:entities/void_blossom", new LootConfig.Pool()
                .rolls(2)
                .add(W2, true)
                .add(A2, true)
                .add(X2)
                .add(R2)
        );

        items.put("bosses_of_mass_destruction:chests/gauntlet", new LootConfig.Pool()
                .rolls(2)
                .add(W2, true)
                .add(A2, true)
                .add(W3, true)
                .add(A3, true)
                .add(W5)
                .add(X3)
                .add(R3)
        );

        items.put("bosses_of_mass_destruction:chests/obsidilith", new LootConfig.Pool()
                .rolls(2)
                .add(W4, true)
                .add(A3, true)
                .add(X4)
                .add(R4)
                .modify(pool -> addWithArsenalSpellBinding(pool, W5, 1))
        );

        // Formidulus mod

        items.put("formidulus:entities/deer_god", new LootConfig.Pool()
                .rolls(2)
                .add(W2, true)
                .add(A2, true)
                .add(X2)
                .add(R2)
                .add(W5)
        );

        // Aehter mod

//        items.put("aether:chests/dungeon/bronze/bronze_dungeon_reward", new LootConfig.Pool()
//                .rolls(0.1)
//                .add(AETHER)
//        );

        items.put("aether:chests/dungeon/silver/silver_dungeon_reward", new LootConfig.Pool()
                .rolls(0.5)
                .add(AETHER).weight(2)
                .add(R2)
        );

        items.put("aether:chests/dungeon/gold/gold_dungeon_reward", new LootConfig.Pool()
                .rolls(1)
                .add(AETHER, true).weight(2)
                .add(R3)
        );

        // Aether villages

        items.put("aether_villages:chests/olympic_citadel/olympic_citadel_treasure", new LootConfig.Pool()
                .rolls(0.5)
                .add(AETHER)
        );

        // Dungeons and Taverns

        // DnT - ancient city

        items.put("nova_structures:chests/ancient_city", new LootConfig.Pool()
                .rolls(0.5)
                .add(A2, true)
                .add(W2, true)
                .add(X2)
        );

        // DnT - desert temple

        items.put("nova_structures:chests/desert_ruins/desert_ruin_lesser_treasure", new LootConfig.Pool()
                .rolls(0.5)
                .add(A1, true)
                .add(W1, true)
                .add(X2)
        );
        scrolls.put("nova_structures:chests/desert_ruins/desert_ruin_lesser_treasure", new LootConfig.Pool()
                .rolls(0.5)
                .scroll(1, 2)
        );
        items.put("nova_structures:chests/undead_crypts_grave", new LootConfig.Pool()
                .rolls(0.5)
                .add(W1)
                .add(X2)
        );
        items.put("nova_structures:chests/desert_ruins/desert_ruin_main_temple", new LootConfig.Pool()
                .rolls(1)
                .add(A1)
                .add(W1)
        );

        // DnT - end castle

        items.put("nova_structures:chests/end_castle/greater_loot", new LootConfig.Pool()
                .rolls(1)
                .add(W4, true)
        );
        items.put("nova_structures:chests/end_castle/lesser_loot", new LootConfig.Pool()
                .rolls(0.5)
                .add(A3, true)
                .add(X4)
        );
        items.put("nova_structures:chests/end_castle/treasure_lighthouse", new LootConfig.Pool()
                .rolls(0.5)
                .add(W4, true)
                .add(X4)
        );
        items.put("nova_structures:chests/end_castle/vault_brigattine", new LootConfig.Pool()
                .rolls(0.5)
                .add(W4, true)
                .add(A3, true)
                .add(X4)
        );
        items.put("nova_structures:chests/end_castle/vault_galleon", new LootConfig.Pool()
                .rolls(0.5)
                .add(W4, true)
                .add(A3, true)
                .add(X4)
                .add(R4)
        );
        items.put("nova_structures:chests/end_castle/vault_slope", new LootConfig.Pool()
                .rolls(0.5)
                .add(W4, true)
                .add(A3, true)
                .add(X4)
        );

        // DnT - nether keep

        items.put("nova_structures:chests/nether_keep/skeleton_tower_chest", new LootConfig.Pool()
                .rolls(0.5)
                .add(W2, true)
                .add(A2)
        );
        items.put("nova_structures:chests/nether_keep/vault_keep", new LootConfig.Pool()
                .rolls(0.5)
                .add(W3, true).weight(2)
                .add(A2).weight(2)
                .add(R2)
        );
        scrolls.put("nova_structures:chests/nether_keep/vault_keep", new LootConfig.Pool()
                .rolls(0.5)
                .scroll(3, 4)
        );

        // DnT - trident_trial_monument

        items.put("nova_structures:chests/trident_trial_monument/ttm_common_vault", new LootConfig.Pool()
                .rolls(0.5)
                .add(W2, true).weight(2)
                .add(X2, true).weight(2)
                .add(R1)
        );
        scrolls.put("nova_structures:chests/trident_trial_monument/ttm_common_vault", new LootConfig.Pool()
                .rolls(0.5)
                .scroll(2, 3)
        );

        // DnT - illager_hideout

        items.put("nova_structures:chests/illager_hideout_lesser_tresure", new LootConfig.Pool()
                .rolls(0.5)
                .add(A1)
                .add(X1)
        );
        items.put("nova_structures:chests/illager_hideout_tresure", new LootConfig.Pool()
                .rolls(1)
                .add(A1)
                .add(W1)
                .add(X1)
        );
        scrolls.put("nova_structures:chests/illager_hideout_tresure", new LootConfig.Pool()
                .rolls(1)
                .scroll(1, 2)
        );
        items.put("nova_structures:chests/illager_outpost_weaponry", new LootConfig.Pool()
                .rolls(1)
                .add(W1)
                .add(X1)
        );
        items.put("nova_structures:chests/pillager_outpost_treasure", new LootConfig.Pool()
                .rolls(1)
                .add(A1)
                .add(W1)
                .add(X1)
        );

        // DnT

        // lone castle

        items.put("nova_structures:chests/lone_citadel/c_vault_boss", new LootConfig.Pool()
                .rolls(1)
                .add(W2, true)
                .add(X2)
        );
        items.put("nova_structures:chests/lone_citadel/c_vault", new LootConfig.Pool()
                .rolls(1)
                .add(A2, true)
                .add(X2)
        );
        scrolls.put("nova_structures:chests/lone_citadel/c_vault", new LootConfig.Pool()
                .rolls(0.5)
                .scroll(2, 3)
        );
        items.put("nova_structures:chests/lone_citadel/c_library", new LootConfig.Pool()
                .rolls(1)
                .add(A2)
        );
        scrolls.put("nova_structures:chests/lone_citadel/c_library", new LootConfig.Pool()
                .rolls(1)
                .scroll(3, 4)
        );
        items.put("nova_structures:chests/lone_citadel/c_forge_chest", new LootConfig.Pool()
                .rolls(1)
                .add(A2)
                .add(A1)
        );

        // bunker_altar

        items.put("nova_structures:chests/bunker_altar", new LootConfig.Pool()
                .rolls(1)
                .add(W1)
                .add(A1)
        );
        scrolls.put("nova_structures:chests/bunker_altar", new LootConfig.Pool()
                .rolls(0.5)
                .scroll(1)
        );

        // conduit_ruin

        items.put("nova_structures:chests/conduit_ruin/conduit_ruin_big", new LootConfig.Pool()
                .rolls(0.5)
                .add(W2)
                .add(A2)
        );

        // creeping_crypt

        items.put("nova_structures:chests/creeping_crypt/crypt_grave", new LootConfig.Pool()
                .rolls(0.5)
                .add(A1)
        );
        items.put("nova_structures:chests/creeping_crypt/crypt_hallway", new LootConfig.Pool()
                .rolls(0.5)
                .add(W1)
        );
        items.put("nova_structures:chests/creeping_crypt/vault_creeping", new LootConfig.Pool()
                .rolls(1)
                .add(A1)
                .add(W1)
                .add(X1)
        );
        scrolls.put("nova_structures:chests/creeping_crypt/vault_creeping", new LootConfig.Pool()
                .rolls(0.5)
                .scroll(1, 2)
        );

        // toxic_lair

        items.put("nova_structures:chests/toxic_lair/toxic_vault", new LootConfig.Pool()
                .rolls(0.5)
                .add(A1)
                .add(W1)
                .add(X1)
        );
        items.put("nova_structures:chests/toxic_lair/toxic_ominous_vault", new LootConfig.Pool()
                .rolls(0.5)
                .add(A2)
                .add(W2, true)
                .add(X2)
        );
        scrolls.put("nova_structures:chests/toxic_lair/toxic_ominous_vault", new LootConfig.Pool()
                .rolls(0.5)
                .scroll(2, 3)
        );
        items.put("nova_structures:chests/toxic_lair/toxic_boss_vault", new LootConfig.Pool()
                .rolls(1)
                .add(A2, true)
                .add(W2, true)
        );


        // MOD CHESTS

        // Graveyard mod

        items.put("graveyard:chests/great_crypt_loot", new LootConfig.Pool()
                .rolls(1)
                .add(A2, true)
                .add(W2, true)
                .add(X2)
        );
        items.put("graveyard:chests/crypt_loot", new LootConfig.Pool()
                .rolls(0.2)
                .add(W1, true)
        );
        items.put("graveyard:chests/small_loot", new LootConfig.Pool()
                .rolls(1)
                .add(W1)
                .add(A1)
        );
        items.put("graveyard:chests/medium_loot", new LootConfig.Pool()
                .rolls(1)
                .add(W1, true)
                .add(A1, true)
        );
        scrolls.put("graveyard:chests/medium_loot", new LootConfig.Pool()
                .rolls(0.5)
                .scroll(2, 2)
        );
        items.put("graveyard:chests/large_loot", new LootConfig.Pool()
                .rolls(1)
                .add(A2, true)
                .add(W2, true)
                .add(X2)
        );

        // Illager Invasion mod

        items.put("illagerinvasion:chests/illager_fort_tower", new LootConfig.Pool()
                .rolls(0.5)
                .add(W1)
                .add(W1, true)
        );
        scrolls.put("illagerinvasion:chests/illager_fort_tower", new LootConfig.Pool()
                .rolls(0.5)
                .scroll(1, 2)
        );

        items.put("illagerinvasion:chests/illusioner_tower_stairs", new LootConfig.Pool()
                .rolls(0.5)
                .add(W1)
                .add(W1, true)
        );
        scrolls.put("illagerinvasion:chests/illusioner_tower_stairs", new LootConfig.Pool()
                .rolls(0.5)
                .scroll(1, 2)
        );
        scrolls.put("illagerinvasion:chests/sorcerer_hut", new LootConfig.Pool()
                .rolls(0.5)
                .scroll(1, 2)
        );
        items.put("illagerinvasion:chests/labirynth", new LootConfig.Pool()
                .rolls(0.3)
                .add(W1)
                .add(W1, true)
                .add(A1, true)
        );
        scrolls.put("illagerinvasion:chests/labirynth", new LootConfig.Pool()
                .rolls(0.5)
                .scroll(1, 2)
        );

        // It takes a pillage

        items.put("takesapillage:chests/bastille/church", new LootConfig.Pool()
                .rolls(1)
                .add(A1, true)
                .add(X1)
        );
        scrolls.put("takesapillage:chests/bastille/church", new LootConfig.Pool()
                .scroll(1, 2)
        );

        // YUNG Better Dungeons mod

        items.put("betterdungeons:skeleton_dungeon/chests/common", new LootConfig.Pool()
                .rolls(0.5)
                .add(W1, true)
        );
        scrolls.put("betterdungeons:skeleton_dungeon/chests/common", new LootConfig.Pool()
                .rolls(0.2)
                .scroll(1, 2)
        );

        items.put("betterdungeons:zombie_dungeon/chests/common", new LootConfig.Pool()
                .rolls(0.5)
                .add(W0)
                .add(W1)
                .add(W2)
                .add(A1)
                .add(X1)
        );
        items.put("betterdungeons:zombie_dungeon/chests/special", new LootConfig.Pool()
                .rolls(0.5)
                .add(W0)
                .add(W1)
                .add(W2)
                .add(A1)
                .add(X1)
        );
        scrolls.put("betterdungeons:zombie_dungeon/chests/special", new LootConfig.Pool()
                .rolls(1)
                .scroll(1, 2)
        );

        items.put("betterdungeons:zombie_dungeon/chests/tombstone", new LootConfig.Pool()
                .rolls(0.5)
                .add(W2, true)
                .add(X2)
        );

        items.put("betterdungeons:small_nether_dungeon/chests/common", new LootConfig.Pool()
                .rolls(0.5)
                .add(GOLDEN)
                .add(W1, true)
                .add(A1, true)
                .add(X1)
        );
        scrolls.put("betterdungeons:small_nether_dungeon/chests/common", new LootConfig.Pool()
                .rolls(0.2)
                .scroll(2, 3)
        );

        // YUNG Better Desert Temples mod

        scrolls.put("betterdeserttemples:chests/library", new LootConfig.Pool()
                .rolls(1)
                .scroll(1, 2)
        );
        items.put("betterdeserttemples:chests/pharaoh_hidden", new LootConfig.Pool()
                .add(GOLDEN, true).weight(2)
                .add(X2)
        );
        items.put("betterdeserttemples:chests/tomb_pharaoh", new LootConfig.Pool()
                .add(GOLDEN, false)
                .add(W1, true).weight(2)
                .add(X2)
        );
        items.put("betterdeserttemples:chests/wardrobe", new LootConfig.Pool()
                .rolls(0.5)
                .add(A1, true)
        );

        // YUNG Better Nether Fortress mod

        items.put("betterfortresses:chests/keep", new LootConfig.Pool()
                .rolls(0.25)
                .add(W0)
                .add(GOLDEN)
                .add(X1)
        );

        // YUNG Better Strongholds mod

        items.put("betterstrongholds:chests/armoury", new LootConfig.Pool()
                .rolls(3)
                .add(W0)
                .add(W1)
                .add(W1, true)
                .add(W2)
                .add(W2, true)
                .add(A1)
                .add(A1, true)
                .add(A2)
                .add(A2, true)
        );
        items.put("betterstrongholds:chests/cmd_yung", new LootConfig.Pool()
                .add(W2, true)
        );
        items.put("betterstrongholds:chests/grand_library", new LootConfig.Pool()
                .rolls(0.5)
                .add(A2, true)
        );
        scrolls.put("betterstrongholds:chests/grand_library", new LootConfig.Pool()
                .rolls(1)
                .scroll(2, 4)
        );
        scrolls.put("betterstrongholds:chests/library_md", new LootConfig.Pool()
                .scroll(2, 3)
        );
        items.put("betterstrongholds:chests/crypt", new LootConfig.Pool()
                .add(W1)
                .add(X1)
        );

        // Philip's Ruins mod

        items.put("philipsruins:chest/lost_soul_city_loot", new LootConfig.Pool()
                .rolls(0.5)
                .add(A2)
                .add(A2, true)
                .add(X2)
        );

        items.put("philipsruins:chest/desert_pyramid_loot", new LootConfig.Pool()
                .add(A1)
                .add(A1, true)
                .add(X1)
        );

        items.put("philipsruins:chest/badlands_dungeon_loot_high", new LootConfig.Pool()
                .add(W1)
                .add(A1)
                .add(X2)
        );

        items.put("philipsruins:chest/level_three_ruins_loot", new LootConfig.Pool()
                .add(W1, true)
                .add(W2)
                .add(A1)
                .add(X1)
        );

        items.put("philipsruins:chest/ocean_ruins_loot", new LootConfig.Pool()
                .add(W1, true)
                .add(W2)
                .add(X1)
        );

        items.put("philipsruins:chest/ocean_ruin_fortress", new LootConfig.Pool()
                .add(W1, true)
                .add(A1, true)
                .add(X1)
        );

        items.put("philipsruins:chest/nether_lava_ruins_loot", new LootConfig.Pool()
                .rolls(0.5)
                .add(A3, true)
                .add(X3)
        );

        items.put("philipsruins:chest/badlands_dungeon_loot_low", new LootConfig.Pool()
                .rolls(0.5)
                .add(A1)
                .add(X1)
        );

        items.put("philipsruins:chest/end_ruins_loot", new LootConfig.Pool()
                .add(W2, true)
                .add(A2, true)
                .add(X3)
                .add(X4)
        );

        items.put("philipsruins:chest/level_one_ruins_loot", new LootConfig.Pool()
                .add(W0)
                .add(A1)
                .add(X1)
        );

        items.put("philipsruins:chest/bone_dungeon_loot", new LootConfig.Pool()
                .rolls(0.5)
                .add(W0)
                .add(W1, false)
                .add(A1)
                .add(A1, true)
                .add(X1)
                .add(X2)
        );

        items.put("philipsruins:chest/ruin_loot", new LootConfig.Pool()
                .rolls(0.5)
                .add(W1)
                .add(A1)
                .add(X1)
        );

        items.put("philipsruins:chest/ancient_ruins_loot", new LootConfig.Pool()
                .rolls(0.5)
                .add(A2)
                .add(X2)
        );

        // Awesome Dungeons mod

        items.put("awesomedungeonnether:chests/awesome_dungeon", new LootConfig.Pool()
                .add(W1)
                .add(X1)
        );

        items.put("awesomedungeonocean:chests/awesome_dungeon", new LootConfig.Pool()
                .add(W1)
                .add(X1)
        );

        items.put("awesomedungeonend:chests/awesome_dungeon", new LootConfig.Pool()
                .add(W3, true)
                .add(A2, true)
                .add(X2)
        );

        items.put("awesomedungeon:chests/awesome_dungeon", new LootConfig.Pool()
                .add(W1)
                .add(X1)
        );

        // Structory mod

        items.put("structory:outcast/bandit/desert_copper", new LootConfig.Pool()
                .rolls(0.5)
                .add(W1, true)
        );

        items.put("structory:outcast/generic/bandit", new LootConfig.Pool()
                .rolls(0.5)
                .add(W1, true)
        );

        items.put("structory:outcast/mine/loot", new LootConfig.Pool()
                .add(W1, true)
                .add(X1)
        );

        items.put("structory:outcast/settlement", new LootConfig.Pool()
                .rolls(0.5)
                .add(W1, true)
                .add(X1)
        );

        items.put("structory:outcast/generic/miner", new LootConfig.Pool()
                .rolls(0.5)
                .add(W1, true)
                .add(X1)
        );

        items.put("structory:outcast/bandit/desert", new LootConfig.Pool()
                .add(W1)
        );

        items.put("structory:outcast/farm_ruin", new LootConfig.Pool()
                .rolls(0.5)
                .add(W0, true)
                .add(W1, true)
        );

        items.put("structory:outcast/ruin/ruin", new LootConfig.Pool()
                .rolls(0.5)
                .add(W1, true)
        );

        // Towns and Towers (kaisyn)

        items.put("kaisyn:village/exclusives/village_piglin_house", new LootConfig.Pool()
                .rolls(0.5)
                .add(GOLDEN)
                .add(W1, true)
                .add(X1)
        );

        items.put("kaisyn:outpost/common/armory", new LootConfig.Pool()
                .add(W1, true)
                .add(W1)
                .add(A1)
        );
        scrolls.put("kaisyn:outpost/common/armory", new LootConfig.Pool()
                .rolls(0.5)
                .scroll(1, 2)
        );

        items.put("kaisyn:village/exclusives/village_piglin_barrel", new LootConfig.Pool()
                .rolls(0.2)
                .add(GOLDEN)
        );

        // Terralith mod

        items.put("terralith:mage/treasure", new LootConfig.Pool()
                .add(A1, true)
                .add(W1, true)
                .add(X1)
        );
        scrolls.put("terralith:mage/treasure", new LootConfig.Pool()
                .scroll(1, 2)
        );

        items.put("terralith:underground/chest", new LootConfig.Pool()
                .rolls(0.5)
                .add(A1, true)
                .add(X1)
        );

        items.put("terralith:spire/common", new LootConfig.Pool()
                .add(GOLDEN)
                .add(W1, true)
                .add(X1)
        );

        items.put("terralith:underground/chest", new LootConfig.Pool()
                .add(GOLDEN)
        );

        items.put("terralith:spire/junk", new LootConfig.Pool()
                .rolls(0.5)
                .add(W0)
                .add(X1)
        );

        items.put("terralith:ruin/glacial/main_cs", new LootConfig.Pool()
                .rolls(0.5)
                .add(W0)
        );

        items.put("terralith:spire/treasure", new LootConfig.Pool()
                .rolls(0.5)
                .bonus_rolls(0)
                .add(W2, true)
                .add(A2, true)
                .add(X2)
        );
        scrolls.put("terralith:spire/treasure", new LootConfig.Pool()
                .rolls(0.5)
                .scroll(2, 3)
        );

        items.put("terralith:desert_outpost", new LootConfig.Pool()
                .add(W1)
        );
        scrolls.put("terralith:desert_outpost", new LootConfig.Pool()
                .rolls(0.5)
                .scroll(1)
        );

        items.put("terralith:ruin/glacial/junk", new LootConfig.Pool()
                .rolls(0.5)
                .add(W0)
                .add(X1)
        );

        // BetterNether mod

        items.put("betternether:chests/wither_tower_bonus", new LootConfig.Pool()
                .add(W4)
                .add(A3)
                .add(X3)
        );

        items.put("betternether:chests/city_surprise", new LootConfig.Pool()
                .rolls(0.5)
                .add(W4)
                .add(X3)
        );

        // BetterEnd mod

        items.put("betterend:chests/shadow_forest", new LootConfig.Pool()
                .rolls(0.5)
                .add(W3)
                .add(X3)
        );

        items.put("betterend:chests/umbrella_jungle", new LootConfig.Pool()
                .rolls(0.5)
                .add(W3)
        );

        items.put("betterend:chests/foggy_mushroomland", new LootConfig.Pool()
                .rolls(0.5)
                .add(W3)
        );

        items.put("betterend:chests/biome", new LootConfig.Pool()
                .rolls(0.5)
                .add(W3)
        );


        // Dungeons Arise mod

        items_regex.put("^dungeons_arise:chests.*barrels$", new LootConfig.Pool()
                .rolls(0.1)
                .add(W1, true)
                .add(A1, true)
                .add(X1)
        );
        items_regex.put("^dungeons_arise:chests.*normal$", new LootConfig.Pool()
                .rolls(0.35)
                .add(W1, true)
                .add(A1, true)
                .add(X1)
        );

        items.put("dungeons_arise:chests/bathhouse/bathhouse_normal", new LootConfig.Pool()
                .rolls(0.4)
                .add(W1, true)
                .add(A1, true)
                .add(X1)
        );
        scrolls.put("dungeons_arise:chests/bathhouse/bathhouse_normal", new LootConfig.Pool()
                .rolls(0.5)
                .scroll(1, 2)
        );
        items.put("dungeons_arise:chests/thornborn_towers/thornborn_towers_top_treasure", new LootConfig.Pool()
                .add(W1)
                .add(A1, true)
                .add(X1)
        );
        items.put("dungeons_arise_seven_seas:chests/victory_frigate/victory_frigate_treasure", new LootConfig.Pool()
                .add(A2, true)
                .add(X2)
        );
        items.put("dungeons_arise:chests/infested_temple/infested_temple_top_treasure", new LootConfig.Pool()
                .add(W2, true)
                .add(A2, true)
                .add(X2)
        );
        scrolls.put("dungeons_arise:chests/infested_temple/infested_temple_top_treasure", new LootConfig.Pool()
                .rolls(0.5)
                .scroll(2, 3)
        );
        items.put("dungeons_arise:chests/illager_windmill/illager_windmill_treasure", new LootConfig.Pool()
                .add(W1, true)
                .add(A1, true)
                .add(X1)
        );
        items.put("dungeons_arise:chests/bandit_towers/bandit_towers_treasure", new LootConfig.Pool()
                .add(W1, true)
                .add(A1, true)
                .add(A2)
                .add(X2)
        );
        scrolls.put("dungeons_arise:chests/bandit_towers/bandit_towers_treasure", new LootConfig.Pool()
                .rolls(0.5)
                .scroll(1, 3)
        );
        items.put("dungeons_arise:chests/ceryneian_hind/ceryneian_hind_treasure", new LootConfig.Pool()
                .add(W2, true)
                .add(A2)
                .add(X2)
        );
        items.put("dungeons_arise:chests/small_blimp/small_blimp_treasure", new LootConfig.Pool()
                .add(A1, true)
                .add(W1, true)
                .add(X1)
        );
        items.put("dungeons_arise:chests/heavenly_conqueror/heavenly_conqueror_treasure", new LootConfig.Pool()
                .add(A2, true)
                .add(W2, true)
                .add(X2)
        );
        scrolls.put("dungeons_arise:chests/heavenly_conqueror/heavenly_conqueror_treasure", new LootConfig.Pool()
                .rolls(0.5)
                .scroll(2, 3)
        );
        items.put("dungeons_arise:chests/aviary/aviary_treasure", new LootConfig.Pool()
                .rolls(0.5)
                .add(W4)
                .add(W3)
                .add(A3)
                .add(X3)
                .add(X4)
        );

        items.put("dungeons_arise:chests/illager_corsair/illager_corsair_treasure", new LootConfig.Pool()
                .add(W1, true)
                .add(A1, true)
                .add(X1)
        );
        items.put("dungeons_arise:chests/typhon/typhon_treasure", new LootConfig.Pool()
                .add(W1, true)
                .add(A1, true)
                .add(X1)
        );
        items.put("dungeons_arise_seven_seas:chests/corsair_corvette/corsair_corvette_treasure", new LootConfig.Pool()
                .add(W1, true)
                .add(A1, true)
                .add(X1)
        );
        scrolls.put("dungeons_arise_seven_seas:chests/corsair_corvette/corsair_corvette_treasure", new LootConfig.Pool()
                .rolls(0.5)
                .scroll(1, 2)
        );
        items.put("dungeons_arise_seven_seas:chests/small_yacht/small_yacht_treasure", new LootConfig.Pool()
                .add(W1, true)
                .add(A1, true)
                .add(X1)
        );
        items.put("dungeons_arise:chests/mushroom_house/mushroom_house_treasure", new LootConfig.Pool()
                .add(W0)
                .add(W1)
                .add(W1, true)
                .add(A1)
                .add(A1, true)
        );
        scrolls.put("dungeons_arise:chests/mushroom_house/mushroom_house_treasure", new LootConfig.Pool()
                .rolls(0.5)
                .scroll(1, 2)
        );
        items.put("dungeons_arise:chests/jungle_tree_house/jungle_tree_house_treasure", new LootConfig.Pool()
                .add(W1, true)
                .add(A1)
                .add(X1)
        );
        scrolls.put("dungeons_arise:chests/jungle_tree_house/jungle_tree_house_treasure", new LootConfig.Pool()
                .rolls(0.2)
                .scroll(1, 2)
        );
        items.put("dungeons_arise:chests/illager_galley/illager_galley_treasure", new LootConfig.Pool()
                .add(W1, true)
                .add(A1, true)
                .add(X1)
        );
        items.put("dungeons_arise:chests/undead_pirate_ship/undead_pirate_ship_treasure", new LootConfig.Pool()
                .add(W1, true)
                .add(A1, true)
                .add(X1)
        );
        items.put("dungeons_arise:chests/heavenly_challenger/heavenly_challenger_treasure", new LootConfig.Pool()
                .add(GOLDEN)
                .add(W2, true)
                .add(W3)
                .add(A2, true)
                .add(A3, true)
                .add(X2)
                .add(X3)
        );
        items.put("dungeons_arise:chests/heavenly_rider/heavenly_rider_treasure", new LootConfig.Pool()
                .add(GOLDEN)
                .add(A2, true)
                .add(X2)
        );
        items.put("dungeons_arise:chests/illager_fort/illager_fort_treasure", new LootConfig.Pool()
                .add(W1)
                .add(A2, true)
                .add(X2)
        );
        scrolls.put("dungeons_arise:chests/illager_fort/illager_fort_treasure", new LootConfig.Pool()
                .rolls(0.5)
                .scroll(1, 3)
        );
        items.put("dungeons_arise:chests/keep_kayra/keep_kayra_treasure", new LootConfig.Pool()
                .add(W2)
                .add(W2, true)
                .add(A2, true)
                .add(A3)
                .add(X2)
                .add(X3)
        );
        scrolls.put("dungeons_arise:chests/keep_kayra/keep_kayra_treasure", new LootConfig.Pool()
                .rolls(0.3)
                .scroll(1, 3)
        );
        items.put("dungeons_arise_seven_seas:chests/pirate_junk/pirate_junk_treasure", new LootConfig.Pool()
                .rolls(0.5)
                .add(A1, true)
                .add(A2)
                .add(X2)
        );
        items.put("dungeons_arise:chests/mushroom_mines/mushroom_mines_treasure", new LootConfig.Pool()
                .add(A1, true)
                .add(A2, true)
                .add(X2)
        );
        items.put("dungeons_arise:chests/mines_treasure_medium", new LootConfig.Pool()
                .add(W1, true)
                .add(A1, true)
                .add(A2, true)
                .add(X2)
        );
        items.put("dungeons_arise:chests/keep_kayra/keep_kayra_library_treasure", new LootConfig.Pool()
                .add(W2, true)
                .add(A2, true)
                .add(A3)
                .add(X2)
                .add(X3)
        );
        scrolls.put("dungeons_arise:chests/keep_kayra/keep_kayra_library_treasure", new LootConfig.Pool()
                .rolls(2)
                .scroll(1, 4)
        );
        items.put("dungeons_arise:chests/mining_system/mining_system_treasure", new LootConfig.Pool()
                .add(A1, true)
                .add(A2, true)
                .add(X2)
        );

        items.put("dungeons_arise:chests/foundry/foundry_treasure", new LootConfig.Pool()
                .add(A2)
                .add(X2)
        );
        items.put("dungeons_arise:chests/keep_kayra/keep_kayra_garden_treasure", new LootConfig.Pool()
                .add(W2, true)
                .add(A2, true)
                .add(A3)
                .add(X2)
                .add(X3)
        );
        items.put("dungeons_arise:chests/plague_asylum/plague_asylum_treasure", new LootConfig.Pool()
                .add(A2, true)
                .add(X2)
        );
        scrolls.put("dungeons_arise:chests/plague_asylum/plague_asylum_treasure", new LootConfig.Pool()
                .rolls(0.3)
                .scroll(1, 3)
        );
        items.put("dungeons_arise_seven_seas:chests/unicorn_galleon/unicorn_galleon_treasure", new LootConfig.Pool()
                .add(A1, true)
                .add(A2, true)
                .add(X2)
        );
        items.put("dungeons_arise:chests/shiraz_palace/shiraz_palace_library", new LootConfig.Pool()
                .rolls(0.5)
                .add(A1)
                .add(A2)
                .add(X2)
        );
        scrolls.put("dungeons_arise:chests/shiraz_palace/shiraz_palace_library", new LootConfig.Pool()
                .scroll(1, 4)
        );
        items.put("dungeons_arise:chests/shiraz_palace/shiraz_palace_elite", new LootConfig.Pool()
                .rolls(2)
                .add(W2, true)
                .add(A2, true)
                .add(A3)
                .add(X2)
                .add(X3)
        );
        items.put("dungeons_arise:chests/shiraz_palace/shiraz_palace_treasure", new LootConfig.Pool()
                .rolls(2)
                .add(W3, true)
                .add(A3, true)
                .add(X3)
                .add(R3)
        );
    }


    private static LootConfig.Pool addWithArsenalSpellBinding(LootConfig.Pool pool, String lootTag, int max) {
        var arsenal_heal_spells = "#arsenal:heal";
        var arsenal_melee_spells = "#arsenal:melee";
        var arsenal_ranged_spells = "#arsenal:ranged";
        var arsenal_shield_spells = "#arsenal:shield";
        var arsenal_spell_spells = "#arsenal:spell";

        return pool.add(lootTag).filter(RPGSeriesItemTags.Archetype.tagString(RPGSeriesItemTags.RoleArchetype.MELEE_DAMAGE))
                .bind(arsenal_melee_spells, 0, max)
                .add(lootTag).filter(RPGSeriesItemTags.Archetype.tagString(RPGSeriesItemTags.RoleArchetype.RANGED_DAMAGE))
                .bind(arsenal_ranged_spells, 0, max)
                .add(lootTag).filter(RPGSeriesItemTags.Archetype.tagString(RPGSeriesItemTags.RoleArchetype.MAGIC_DAMAGE))
                .bind(arsenal_spell_spells, 0, max)
                .add(lootTag).filter(RPGSeriesItemTags.Archetype.tagString(RPGSeriesItemTags.RoleArchetype.DEFENSE))
                .bind(arsenal_shield_spells, 0, max)
                .add(lootTag).filter(RPGSeriesItemTags.Archetype.tagString(RPGSeriesItemTags.RoleArchetype.HEALING))
                .bind(arsenal_heal_spells, 0, max);
    }
}
