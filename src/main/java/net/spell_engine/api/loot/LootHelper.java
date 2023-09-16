package net.spell_engine.api.loot;

import net.minecraft.item.Item;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.function.EnchantWithLevelsLootFunction;
import net.minecraft.loot.provider.number.BinomialLootNumberProvider;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.loot.provider.number.UniformLootNumberProvider;
import net.minecraft.util.Identifier;

import java.util.HashMap;

public class LootHelper {
    public static void configure(Identifier id, LootTable.Builder tableBuilder, LootConfig config, HashMap<String, Item> entries) {
        var groups = config.loot_tables.get(id.toString());
        if (groups != null) {
            for(var groupName: groups) {
                var group = config.item_groups.get(groupName);
                if (group == null || group.ids.isEmpty() || group.weight <= 0) { continue; }
                var chance = group.chance > 0 ? group.chance : 1F;
                LootPool.Builder lootPoolBuilder = LootPool.builder();
                lootPoolBuilder.rolls(BinomialLootNumberProvider.create(1, chance));
                lootPoolBuilder.bonusRolls(ConstantLootNumberProvider.create(group.bonus_roll_chance));
                for (var entryId: group.ids) {
                    var item = entries.get(entryId);
                    if (item == null) { continue; }
                    var itemEntry = ItemEntry.builder(item)
                            .weight(group.weight);

                    if (group.enchant != null && group.enchant.isValid()) {
                        var enchantFunction = EnchantWithLevelsLootFunction.builder(UniformLootNumberProvider.create(group.enchant.min_power, group.enchant.max_power));
                        if (group.enchant.allow_treasure) {
                            enchantFunction.allowTreasureEnchantments();
                        }
                        itemEntry.apply(enchantFunction);
                    }
                    lootPoolBuilder.with(itemEntry);
                }
                tableBuilder.pool(lootPoolBuilder.build());
            }
        }
    }

//    private static TagKey<Item> itemTagKey(String id) {
//        return TagKey.of(Registries.ITEM, new Identifier(id));
//    }
}
