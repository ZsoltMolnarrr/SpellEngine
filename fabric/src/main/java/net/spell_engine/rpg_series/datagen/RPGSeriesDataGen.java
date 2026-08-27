package net.spell_engine.rpg_series.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.spell_engine.rpg_series.item.Equipment;
import net.spell_engine.rpg_series.item.Armor;
import net.spell_engine.rpg_series.item.Weapon;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.api.tags.SpellTags;
import net.spell_engine.rpg_series.tags.RPGSeriesItemTags;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class RPGSeriesDataGen {
    public record ShieldEntry(Identifier id, Equipment.LootProperties lootProperties) {}
    public record BowEntry(Identifier id, Equipment.WeaponType weaponType, Equipment.LootProperties lootProperties) {}

    public static abstract class ItemTagGenerator extends FabricTagsProvider<Item> {
        public ItemTagGenerator(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
            super(output, Registries.ITEM, registriesFuture);
        }

        public void generateWeaponTags(List<Weapon.Entry> weapons) {
            for (var weapon: weapons) {
                var weaponType = RPGSeriesItemTags.WeaponType.get(weapon.category());
                var weaponTag = builder(weaponType);
                weaponTag.addOptional(ResourceKey.create(Registries.ITEM, weapon.id()));

                var tier = weapon.lootProperties().tier();
                if (tier >= 0) {
                    var tierTag = builder(RPGSeriesItemTags.LootTiers.get(tier, RPGSeriesItemTags.LootCategory.WEAPONS));
                    tierTag.addOptional(ResourceKey.create(Registries.ITEM, weapon.id()));
                }

                var lootTheme = weapon.lootProperties().theme();
                if (lootTheme != null && !lootTheme.isEmpty()) {
                    var themeTag = builder(RPGSeriesItemTags.LootThemes.get(lootTheme));
                    themeTag.addOptional(ResourceKey.create(Registries.ITEM, weapon.id()));
                }
            }
        }

        public record ArmorOptions(
                boolean allowLootTierTags,
                boolean allowLootThemeTags
        ) {
            public static final ArmorOptions DEFAULT = new ArmorOptions(true, true);
        }

        public void generateArmorTags(List<Armor.Entry> armors) {
            generateArmorTags(armors, EnumSet.noneOf(RPGSeriesItemTags.ArmorMetaType.class));
        }

        public void generateArmorTags(List<Armor.Entry> armors, ArmorOptions options) {
            generateArmorTags(armors, EnumSet.noneOf(RPGSeriesItemTags.ArmorMetaType.class), options);
        }

        public void generateArmorTags(List<Armor.Entry> armors, RPGSeriesItemTags.ArmorMetaType metaType) {
            generateArmorTags(armors, EnumSet.of(metaType));
        }

        public void generateArmorTags(List<Armor.Entry> armors, RPGSeriesItemTags.ArmorMetaType metaType, ArmorOptions options) {
            generateArmorTags(armors, EnumSet.of(metaType), options);
        }

        public void generateArmorTags(List<Armor.Entry> armors, EnumSet<RPGSeriesItemTags.ArmorMetaType> metaTypes) {
            generateArmorTags(armors, metaTypes, ArmorOptions.DEFAULT);
        }

        public void generateArmorTags(List<Armor.Entry> armors, EnumSet<RPGSeriesItemTags.ArmorMetaType> metaTypes, ArmorOptions options) {
            for (var armor: armors) {

                var set = armor.armorSet();
                var headTag = builder(ItemTags.HEAD_ARMOR);
                headTag.add(ResourceKey.create(Registries.ITEM, BuiltInRegistries.ITEM.getKey(set.head)));
                var chestTag = builder(ItemTags.CHEST_ARMOR);
                chestTag.add(ResourceKey.create(Registries.ITEM, BuiltInRegistries.ITEM.getKey(set.chest)));
                var legsTag = builder(ItemTags.LEG_ARMOR);
                legsTag.add(ResourceKey.create(Registries.ITEM, BuiltInRegistries.ITEM.getKey(set.legs)));
                var feetTag = builder(ItemTags.FOOT_ARMOR);
                feetTag.add(ResourceKey.create(Registries.ITEM, BuiltInRegistries.ITEM.getKey(set.feet)));

                var tier = armor.lootProperties().tier();
                if (options.allowLootTierTags && tier >= 0) {
                    var tierTag = builder(RPGSeriesItemTags.LootTiers.get(tier, RPGSeriesItemTags.LootCategory.ARMORS));
                    for (var id: armor.armorSet().pieceIds()) {
                        tierTag.addOptional(ResourceKey.create(Registries.ITEM, (Identifier) id));
                    }
                }

                var lootTheme = armor.lootProperties().theme();
                if (options.allowLootThemeTags && lootTheme != null && !lootTheme.isEmpty()) {
                    var themeTag = builder(RPGSeriesItemTags.LootThemes.get(lootTheme));
                    for (var id: armor.armorSet().pieceIds()) {
                        themeTag.addOptional(ResourceKey.create(Registries.ITEM, (Identifier) id));
                    }
                }

                for (var metaType: metaTypes) {
                    var metaTag = builder(RPGSeriesItemTags.ArmorType.get(metaType));
                    for (var id: armor.armorSet().pieceIds()) {
                        metaTag.addOptional(ResourceKey.create(Registries.ITEM, (Identifier) id));
                    }
                }
            }
        }

        public void generateBowTags(List<BowEntry> bows) {
            for (var entry: bows) {
                var id = entry.id();
                var weaponType = RPGSeriesItemTags.WeaponType.get(entry.weaponType());
                var weaponTag = builder(weaponType);
                weaponTag.addOptional(ResourceKey.create(Registries.ITEM, (Identifier) id));
            }
            generateLootTags(bows.stream().collect(Collectors.toMap(BowEntry::id, BowEntry::lootProperties)),
                    RPGSeriesItemTags.LootCategory.WEAPONS);
        }

        public void generateShieldTags(List<ShieldEntry> shields) {
            for (var entry: shields) {
                var id = entry.id();
                var weaponType = RPGSeriesItemTags.WeaponType.get(Equipment.WeaponType.SHIELD);
                var weaponTag = builder(weaponType);
                weaponTag.addOptional(ResourceKey.create(Registries.ITEM, (Identifier) id));
            }
            generateLootTags(shields.stream().collect(Collectors.toMap(ShieldEntry::id, ShieldEntry::lootProperties)),
                    RPGSeriesItemTags.LootCategory.WEAPONS);
        }

        public void generateAccessoryTags(Map<Identifier, Equipment.LootProperties> accessories) {
            generateLootTags(accessories, RPGSeriesItemTags.LootCategory.ACCESSORIES);
        }

        public void generateRelicTags(Map<Identifier, Equipment.LootProperties> relics) {
            generateLootTags(relics, RPGSeriesItemTags.LootCategory.RELICS);
        }

        public void generateLootTags(Map<Identifier, Equipment.LootProperties> items, RPGSeriesItemTags.LootCategory category) {
            for (var entry: items.entrySet()) {
                var id = entry.getKey();
                var lootProperties = entry.getValue();

                var tier = lootProperties.tier();
                if (tier >= 0) {
                    var tierTag = builder(RPGSeriesItemTags.LootTiers.get(tier, category));
                    tierTag.addOptional(ResourceKey.create(Registries.ITEM, (Identifier) id));
                }

                var lootTheme = lootProperties.theme();
                if (lootTheme != null && !lootTheme.isEmpty()) {
                    var themeTag = builder(RPGSeriesItemTags.LootThemes.get(lootTheme));
                    themeTag.addOptional(ResourceKey.create(Registries.ITEM, (Identifier) id));
                }
            }
        }
    }

    public static abstract class SpellTagGenerator extends FabricTagsProvider<Spell> {
        public SpellTagGenerator(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
            super(output, SpellRegistry.KEY, registriesFuture);
        }

        public void generateScrollTag(String namespace, String scroll, List<Identifier> spellIds) {
            TagKey<Spell> tagKey = SpellTags.spellScroll(namespace, scroll);
            var scrollTag = builder(tagKey);
            for (var id: spellIds) {
                scrollTag.add(ResourceKey.create(SpellRegistry.KEY, id));
            }
        }

        public void generateBookTag(String namespace, String book, List<Identifier> spellIds) {
            TagKey<Spell> tagKey = SpellTags.spellBook(namespace, book);
            var bookTag = builder(tagKey);
            for (var id: spellIds) {
                bookTag.add(ResourceKey.create(SpellRegistry.KEY, id));
            }
        }

        public void generateWeaponTag(String namespace, String weapon, List<Identifier> spellIds) {
            TagKey<Spell> tagKey = SpellTags.weapon(namespace, weapon);
            var weaponTag = builder(tagKey);
            for (var id: spellIds) {
                weaponTag.add(ResourceKey.create(SpellRegistry.KEY, id));
            }
        }
    }

    @SafeVarargs
    public static <E> List<E> combine(final List<E> ... smallLists) {
        final ArrayList<E> bigList = new ArrayList<E>();
        for (final List<E> list: smallLists) {
            bigList.addAll(list);
        }
        return bigList;
    }
}
