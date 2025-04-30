package net.spell_engine.rpg_series.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.spell_engine.api.item.Equipment;
import net.spell_engine.api.item.armor.Armor;
import net.spell_engine.api.item.weapon.Weapon;
import net.spell_engine.api.tags.SpellEngineItemTags;
import net.spell_engine.rpg_series.tags.RPGSeriesItemTags;
import net.spell_power.SpellPowerMod;
import net.spell_power.api.SpellPowerTags;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class RPGSeriesDataGen {
    public static class BaselineTagGenerator extends FabricTagProvider<Item> {
        public BaselineTagGenerator(FabricDataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> registriesFuture) {
            super(output, RegistryKeys.ITEM, registriesFuture);
        }

        @Override
        protected void configure(RegistryWrapper.WrapperLookup wrapperLookup) {
            for (var entry: RPGSeriesItemTags.WeaponType.ALL.entrySet()) {
                var tag = getOrCreateTagBuilder(entry.getValue());
            }
            for (var archetype: RPGSeriesItemTags.RoleArchetype.values()) {
                var tag = getOrCreateTagBuilder(RPGSeriesItemTags.Archetype.tag(archetype));
                for (var entry: RPGSeriesItemTags.WeaponType.ALL.entrySet()) {
                    if (RPGSeriesItemTags.Archetype.classify(entry.getKey()) == archetype) {
                        tag.addTag(entry.getValue());
                    }
                }
            }
            for (var entry: RPGSeriesItemTags.LootThemes.ALL.entrySet()) {
                var tag = getOrCreateTagBuilder(entry.getValue());
            }
            for (int i = 0; i < RPGSeriesItemTags.LootTiers.DEFAULT_TIERS; i++) {
                for (var category: RPGSeriesItemTags.LootCategory.values()) {
                    var tag = getOrCreateTagBuilder(RPGSeriesItemTags.LootTiers.get(i, category));
                }
            }

            var fullSpellWeaponTypes = List.of(
                    Equipment.WeaponType.DAMAGE_STAFF, Equipment.WeaponType.DAMAGE_WAND,
                    Equipment.WeaponType.HEALING_STAFF, Equipment.WeaponType.HEALING_WAND,
                    Equipment.WeaponType.SPELL_BLADE, Equipment.WeaponType.SPELL_SCYTHE
            );
            var meleeSpellWeaponTypes = List.of(
                    Equipment.WeaponType.SWORD,
                    Equipment.WeaponType.CLAYMORE, Equipment.WeaponType.MACE, Equipment.WeaponType.HAMMER,
                    Equipment.WeaponType.GLAIVE
            );

            /// Spell Infinity enchantables
            var spellInfinityTypes = combine(fullSpellWeaponTypes, meleeSpellWeaponTypes);
            var spellInfinityTag = getOrCreateTagBuilder(SpellEngineItemTags.ENCHANTABLE_SPELL_INFINITY);
            for (var type: spellInfinityTypes) {
                spellInfinityTag.addTag(RPGSeriesItemTags.WeaponType.get(type));
            }

            /// Spell Haste enchantables
            var spellHasteTag = getOrCreateTagBuilder(SpellPowerTags.Items.Enchantable.HASTE);
            for (var type: fullSpellWeaponTypes) {
                spellHasteTag.addTag(RPGSeriesItemTags.WeaponType.get(type));
            }

            /// Amplify Spell enchantables
            var criticalDamageTag = getOrCreateTagBuilder(SpellPowerTags.Items.Enchantable.CRITICAL_DAMAGE);
            for (var type: fullSpellWeaponTypes) {
                criticalDamageTag.addTag(RPGSeriesItemTags.WeaponType.get(type));
            }

            /// Spell Power enchantables
            var spellPowerTypes = combine(fullSpellWeaponTypes, meleeSpellWeaponTypes);
            var spellPowerTag = getOrCreateTagBuilder(SpellPowerTags.Items.Enchantable.SPELL_POWER_GENERIC);
            for (var type: spellPowerTypes) {
                spellPowerTag.addTag(RPGSeriesItemTags.WeaponType.get(type));
            }

            /// Unbreaking enchantables
            var unbreakingTypes = Equipment.WeaponType.values();
            var unbreakingTag = getOrCreateTagBuilder(ItemTags.DURABILITY_ENCHANTABLE);
            for (var type: unbreakingTypes) {
                unbreakingTag.addTag(RPGSeriesItemTags.WeaponType.get(type));
            }

            /// Sharpness enchantables
            var sharpWeaponTypes = List.of(
                    Equipment.WeaponType.SWORD, Equipment.WeaponType.SPEAR,
                    Equipment.WeaponType.CLAYMORE, Equipment.WeaponType.MACE, Equipment.WeaponType.HAMMER,
                    Equipment.WeaponType.DAGGER, Equipment.WeaponType.SICKLE, Equipment.WeaponType.DOUBLE_AXE,
                    Equipment.WeaponType.GLAIVE, Equipment.WeaponType.SPELL_BLADE, Equipment.WeaponType.SPELL_SCYTHE
            );
            var sharpTag = getOrCreateTagBuilder(ItemTags.SHARP_WEAPON_ENCHANTABLE);
            for (var type: sharpWeaponTypes) {
                sharpTag.addTag(RPGSeriesItemTags.WeaponType.get(type));
            }

            /// Melee enchantables
            var meleeWeaponTypes = List.of(
                    Equipment.WeaponType.SWORD, Equipment.WeaponType.CLAYMORE, Equipment.WeaponType.MACE, Equipment.WeaponType.HAMMER,
                    Equipment.WeaponType.SPEAR, Equipment.WeaponType.DAGGER, Equipment.WeaponType.SICKLE, Equipment.WeaponType.DOUBLE_AXE,
                    Equipment.WeaponType.GLAIVE
            );
            var meleeTag = getOrCreateTagBuilder(ItemTags.SWORDS);
            for (var type: meleeWeaponTypes) {
                meleeTag.addTag(RPGSeriesItemTags.WeaponType.get(type));
            }

            /// Ranged enchantables
            var bowTypes = List.of(Equipment.WeaponType.SHORT_BOW, Equipment.WeaponType.LONG_BOW);
            for (var type: bowTypes) {
                var tag = getOrCreateTagBuilder(ItemTags.BOW_ENCHANTABLE);
                tag.addTag(RPGSeriesItemTags.WeaponType.get(type));
            }
            var crossbowTypes = List.of(Equipment.WeaponType.RAPID_CROSSBOW, Equipment.WeaponType.HEAVY_CROSSBOW);
            for (var type: crossbowTypes) {
                var tag = getOrCreateTagBuilder(ItemTags.CROSSBOW_ENCHANTABLE);
                tag.addTag(RPGSeriesItemTags.WeaponType.get(type));
            }
        }
    }

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

        public void generateArmorTags(List<Armor.Entry> armors) {
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
                if (tier >= 0) {
                    var tierTag = getOrCreateTagBuilder(RPGSeriesItemTags.LootTiers.get(tier, RPGSeriesItemTags.LootCategory.ARMORS));
                    for (var id: armor.armorSet().pieceIds()) {
                        tierTag.addOptional((Identifier) id);
                    }
                }

                var lootTheme = armor.lootProperties().theme();
                if (lootTheme != null && !lootTheme.isEmpty()) {
                    var themeTag = getOrCreateTagBuilder(RPGSeriesItemTags.LootThemes.get(lootTheme));
                    for (var id: armor.armorSet().pieceIds()) {
                        themeTag.addOptional((Identifier) id);
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

    @SafeVarargs
    public static <E> List<E> combine(final List<E> ... smallLists) {
        final ArrayList<E> bigList = new ArrayList<E>();
        for (final List<E> list: smallLists) {
            bigList.addAll(list);
        }
        return bigList;
    }
}
