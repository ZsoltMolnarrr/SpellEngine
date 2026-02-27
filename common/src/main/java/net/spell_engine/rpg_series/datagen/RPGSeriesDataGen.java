package net.spell_engine.rpg_series.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
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

    public static abstract class ItemTagGenerator extends FabricTagProvider<Item> {
        public ItemTagGenerator(FabricDataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> registriesFuture) {
            super(output, RegistryKeys.ITEM, registriesFuture);
        }

        public void generateWeaponTags(List<Weapon.Entry> weapons) {
            for (var weapon: weapons) {
                var weaponType = RPGSeriesItemTags.WeaponType.get(weapon.category());
                var weaponTag = getOrCreateTagBuilder(weaponType);
                weaponTag.addOptional(weapon.id());

                var tier = weapon.lootProperties().tier();
                if (tier >= 0) {
                    var tierTag = getOrCreateTagBuilder(RPGSeriesItemTags.LootTiers.get(tier, RPGSeriesItemTags.LootCategory.WEAPONS));
                    tierTag.addOptional(weapon.id());
                }

                var lootTheme = weapon.lootProperties().theme();
                if (lootTheme != null && !lootTheme.isEmpty()) {
                    var themeTag = getOrCreateTagBuilder(RPGSeriesItemTags.LootThemes.get(lootTheme));
                    themeTag.addOptional(weapon.id());
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
                var headTag = getOrCreateTagBuilder(ItemTags.HEAD_ARMOR);
                headTag.add(set.head);
                var chestTag = getOrCreateTagBuilder(ItemTags.CHEST_ARMOR);
                chestTag.add(set.chest);
                var legsTag = getOrCreateTagBuilder(ItemTags.LEG_ARMOR);
                legsTag.add(set.legs);
                var feetTag = getOrCreateTagBuilder(ItemTags.FOOT_ARMOR);
                feetTag.add(set.feet);

                var tier = armor.lootProperties().tier();
                if (options.allowLootTierTags && tier >= 0) {
                    var tierTag = getOrCreateTagBuilder(RPGSeriesItemTags.LootTiers.get(tier, RPGSeriesItemTags.LootCategory.ARMORS));
                    for (var id: armor.armorSet().pieceIds()) {
                        tierTag.addOptional((Identifier) id);
                    }
                }

                var lootTheme = armor.lootProperties().theme();
                if (options.allowLootThemeTags && lootTheme != null && !lootTheme.isEmpty()) {
                    var themeTag = getOrCreateTagBuilder(RPGSeriesItemTags.LootThemes.get(lootTheme));
                    for (var id: armor.armorSet().pieceIds()) {
                        themeTag.addOptional((Identifier) id);
                    }
                }

                for (var metaType: metaTypes) {
                    var metaTag = getOrCreateTagBuilder(RPGSeriesItemTags.ArmorType.get(metaType));
                    for (var id: armor.armorSet().pieceIds()) {
                        metaTag.addOptional((Identifier) id);
                    }
                }
            }
        }

        public void generateBowTags(List<BowEntry> bows) {
            for (var entry: bows) {
                var id = entry.id();
                var weaponType = RPGSeriesItemTags.WeaponType.get(entry.weaponType());
                var weaponTag = getOrCreateTagBuilder(weaponType);
                weaponTag.addOptional(id);
            }
            generateLootTags(bows.stream().collect(Collectors.toMap(BowEntry::id, BowEntry::lootProperties)),
                    RPGSeriesItemTags.LootCategory.WEAPONS);
        }

        public void generateShieldTags(List<ShieldEntry> shields) {
            for (var entry: shields) {
                var id = entry.id();
                var weaponType = RPGSeriesItemTags.WeaponType.get(Equipment.WeaponType.SHIELD);
                var weaponTag = getOrCreateTagBuilder(weaponType);
                weaponTag.addOptional(id);
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
                    var tierTag = getOrCreateTagBuilder(RPGSeriesItemTags.LootTiers.get(tier, category));
                    tierTag.addOptional(id);
                }

                var lootTheme = lootProperties.theme();
                if (lootTheme != null && !lootTheme.isEmpty()) {
                    var themeTag = getOrCreateTagBuilder(RPGSeriesItemTags.LootThemes.get(lootTheme));
                    themeTag.addOptional(id);
                }
            }
        }
    }

    public static abstract class SpellTagGenerator extends FabricTagProvider<Spell> {
        public SpellTagGenerator(FabricDataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> registriesFuture) {
            super(output, SpellRegistry.KEY, registriesFuture);
        }

        public void generateScrollTag(String namespace, String scroll, List<Identifier> spellIds) {
            TagKey<Spell> tagKey = SpellTags.spellScroll(namespace, scroll);
            var scrollTag = getOrCreateTagBuilder(tagKey);
            for (var id: spellIds) {
                scrollTag.add(id);
            }
        }

        public void generateBookTag(String namespace, String book, List<Identifier> spellIds) {
            TagKey<Spell> tagKey = SpellTags.spellBook(namespace, book);
            var bookTag = getOrCreateTagBuilder(tagKey);
            for (var id: spellIds) {
                bookTag.add(id);
            }
        }

        public void generateWeaponTag(String namespace, String weapon, List<Identifier> spellIds) {
            TagKey<Spell> tagKey = SpellTags.weapon(namespace, weapon);
            var weaponTag = getOrCreateTagBuilder(tagKey);
            for (var id: spellIds) {
                weaponTag.add(id);
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
