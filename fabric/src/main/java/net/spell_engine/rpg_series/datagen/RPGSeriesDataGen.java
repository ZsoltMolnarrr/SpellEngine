package net.spell_engine.rpg_series.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryBuilder;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.spell_engine.api.item.set.EquipmentSetRegistry;
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
                // 1.20.1: no `minecraft:head_armor` / `chest_armor` / `leg_armor` / `foot_armor` item tags;
                // armor enchantability comes from `ArmorItem` (`EnchantmentTarget.ARMOR_*`) instead.
                // Those tags feed `#minecraft:trimmable_armor` on 1.20.5+, which is what made RPG armor
                // trimmable implicitly there. On 1.20.1 `#minecraft:trimmable_armor` is an explicit list,
                // so armor must opt in directly - both `SmithingTrimRecipe`'s base ingredient and
                // `ArmorTrim.apply` gate on it.
                var trimmableTag = getOrCreateTagBuilder(ItemTags.TRIMMABLE_ARMOR);
                for (var id: armor.armorSet().pieceIds()) {
                    trimmableTag.addOptional((Identifier) id);
                }

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

    /// Registers Spell Engine's dynamic registries on the datagen `RegistryBuilder`:
    /// `spell_engine:spell` ([SpellRegistry#KEY]) and `equipment_set` ([EquipmentSetRegistry#KEY]).
    ///
    /// Fabric's `DynamicRegistries.registerSynced` only feeds the *runtime* `RegistryLoader`, while the datagen
    /// `WrapperLookup` is assembled from `BuiltinRegistries.REGISTRY_BUILDER` plus whatever each
    /// `DataGeneratorEntrypoint` adds here. Without the spell registry any [SpellTagGenerator] fails with
    /// `Registry spell_engine:spell not found`; without the equipment-set registry
    /// [net.spell_engine.api.datagen.EquipmentSetGenerator] (or any `FabricDynamicRegistryProvider` adding sets)
    /// fails with `Registry equipment_set is not loaded from datapacks`.
    ///
    /// Consumer usage — one line in the mod's `DataGeneratorEntrypoint`:
    /// ```java
    /// @Override
    /// public void buildRegistry(RegistryBuilder registryBuilder) { RPGSeriesDataGen.buildRegistry(registryBuilder); }
    /// ```
    /// Both bootstraps are intentionally empty: spell tags are written with `addOptional`/`addOptionalTag` and
    /// equipment sets are *added* by the provider, so no entries have to exist at datagen time. Mods that
    /// registered `EquipmentSetRegistry.KEY` themselves next to this call must drop that line (`RegistryBuilder`
    /// rejects a duplicate key).
    public static void buildRegistry(RegistryBuilder registryBuilder) {
        registryBuilder.addRegistry(SpellRegistry.KEY, context -> { });
        registryBuilder.addRegistry(EquipmentSetRegistry.KEY, context -> { });
    }

    /// Base class for spell tag providers (spell book / scroll / weapon tags).
    ///
    /// **The owning `DataGeneratorEntrypoint` must also override `buildRegistry` and call
    /// [RPGSeriesDataGen#buildRegistry(RegistryBuilder)]**, otherwise datagen throws
    /// `Registry spell_engine:spell not found`.
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
