package net.spell_engine.rpg_series.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.spell_engine.api.datagen.NamespacedLangGenerator;
import net.spell_engine.api.datagen.SpellGenerator;
import net.spell_engine.rpg_series.item.Equipment;
import net.spell_engine.api.tags.SpellEngineItemTags;
import net.spell_engine.rpg_series.tags.RPGSeriesItemTags;
import net.spell_power.api.SpellPowerTags;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class RPGSeriesContent {
    public static class EquipmentTagGen extends FabricTagProvider.ItemTagProvider {
        public EquipmentTagGen(FabricDataOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
            super(output, registriesFuture);
        }

        /// Vanilla gear classified into RPG Series loot tiers. Loot tables dropping these get the
        /// matching `loot_tier` items injected by the loot fallback injector.
        private void generateLootReferenceTags() {
            var WEAPONS = RPGSeriesItemTags.LootCategory.WEAPONS;
            var ARMORS = RPGSeriesItemTags.LootCategory.ARMORS;
            for (int i = 0; i < RPGSeriesItemTags.LootTiers.DEFAULT_TIERS; i++) {
                valueLookupBuilder(RPGSeriesItemTags.LootReference.get(i, WEAPONS));
                valueLookupBuilder(RPGSeriesItemTags.LootReference.get(i, ARMORS));
                valueLookupBuilder(RPGSeriesItemTags.LootReference.treasures(i));
            }
            // Treasure signals: valuables that only show up in loot, used for accessories / relics
            valueLookupBuilder(RPGSeriesItemTags.LootReference.treasures(1))
                    .add(Items.EMERALD, Items.ENCHANTED_BOOK, Items.GOLDEN_APPLE);
            valueLookupBuilder(RPGSeriesItemTags.LootReference.treasures(2))
                    .add(Items.DIAMOND, Items.HEART_OF_THE_SEA, Items.ENCHANTED_GOLDEN_APPLE, Items.DIAMOND_HORSE_ARMOR,
                         Items.MUSIC_DISC_13, Items.MUSIC_DISC_CAT, Items.MUSIC_DISC_OTHERSIDE,
                         Items.MUSIC_DISC_PIGSTEP, Items.MUSIC_DISC_RELIC, Items.MUSIC_DISC_5);
            valueLookupBuilder(RPGSeriesItemTags.LootReference.treasures(3))
                    .add(Items.TOTEM_OF_UNDYING, Items.NETHERITE_INGOT, Items.NETHERITE_SCRAP,
                         Items.NETHER_STAR, Items.ELYTRA);
            valueLookupBuilder(RPGSeriesItemTags.LootReference.get(0, WEAPONS))
                    .add(Items.WOODEN_SWORD, Items.WOODEN_AXE,
                         Items.STONE_SWORD, Items.STONE_AXE);
            valueLookupBuilder(RPGSeriesItemTags.LootReference.get(RPGSeriesItemTags.LootReference.GOLDEN_WEAPONS))
                    .add(Items.GOLDEN_SWORD, Items.GOLDEN_AXE);
            valueLookupBuilder(RPGSeriesItemTags.LootReference.get(0, ARMORS))
                    .add(Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS,
                         Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS);
            valueLookupBuilder(RPGSeriesItemTags.LootReference.get(1, WEAPONS))
                    .add(Items.IRON_SWORD, Items.IRON_AXE, Items.BOW, Items.CROSSBOW);
            valueLookupBuilder(RPGSeriesItemTags.LootReference.get(1, ARMORS))
                    .add(Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS,
                         Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS,
                         Items.TURTLE_HELMET);
            valueLookupBuilder(RPGSeriesItemTags.LootReference.get(2, WEAPONS))
                    .add(Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.TRIDENT);
            valueLookupBuilder(RPGSeriesItemTags.LootReference.get(2, ARMORS))
                    .add(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS);
            valueLookupBuilder(RPGSeriesItemTags.LootReference.get(3, WEAPONS))
                    .add(Items.NETHERITE_SWORD, Items.NETHERITE_AXE, Items.MACE);
            valueLookupBuilder(RPGSeriesItemTags.LootReference.get(3, ARMORS))
                    .add(Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS);
        }

        @Override
        protected void addTags(HolderLookup.Provider wrapperLookup) {
            for (var entry: RPGSeriesItemTags.WeaponType.ALL.entrySet()) {
                var tag = valueLookupBuilder(entry.getValue());
            }
            for (var archetype: RPGSeriesItemTags.RoleArchetype.values()) {
                var tag = valueLookupBuilder(RPGSeriesItemTags.Archetype.tag(archetype));
                for (var entry: RPGSeriesItemTags.WeaponType.ALL.entrySet()) {
                    if (RPGSeriesItemTags.Archetype.classify(entry.getKey()) == archetype) {
                        tag.addTag(entry.getValue());
                    }
                }
            }
            for (var entry: RPGSeriesItemTags.LootThemes.ALL.entrySet()) {
                var tag = valueLookupBuilder(entry.getValue());
            }
            for (int i = 0; i < RPGSeriesItemTags.LootTiers.DEFAULT_TIERS; i++) {
                for (var category: RPGSeriesItemTags.LootCategory.values()) {
                    var tag = valueLookupBuilder(RPGSeriesItemTags.LootTiers.get(i, category));
                }
            }
            for (var entry: RPGSeriesItemTags.ArmorType.ALL.entrySet()) {
                var tag = valueLookupBuilder(entry.getValue());
            }
            generateLootReferenceTags();

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
            var spellInfinityTypes = RPGSeriesDataGen.combine(fullSpellWeaponTypes, meleeSpellWeaponTypes);
            var spellInfinityTag = valueLookupBuilder(SpellEngineItemTags.ENCHANTABLE_SPELL_INFINITY);
            for (var type: spellInfinityTypes) {
                spellInfinityTag.addTag(RPGSeriesItemTags.WeaponType.get(type));
            }

            /// Spell Haste enchantables
            var spellHasteTag = valueLookupBuilder(SpellPowerTags.Items.Enchantable.HASTE);
            for (var type: fullSpellWeaponTypes) {
                spellHasteTag.addTag(RPGSeriesItemTags.WeaponType.get(type));
            }

            /// Amplify Spell enchantables
            var criticalDamageTag = valueLookupBuilder(SpellPowerTags.Items.Enchantable.CRITICAL_DAMAGE);
            for (var type: fullSpellWeaponTypes) {
                criticalDamageTag.addTag(RPGSeriesItemTags.WeaponType.get(type));
            }

            /// Spell Power enchantables
            var spellPowerTypes = RPGSeriesDataGen.combine(fullSpellWeaponTypes, meleeSpellWeaponTypes);
            var spellPowerTag = valueLookupBuilder(SpellPowerTags.Items.Enchantable.SPELL_POWER_GENERIC);
            for (var type: spellPowerTypes) {
                spellPowerTag.addTag(RPGSeriesItemTags.WeaponType.get(type));
            }

            /// Spell Volatility enchantables
            var spellVolatilityTag = valueLookupBuilder(SpellPowerTags.Items.Enchantable.CRITICAL_CHANCE);
            for (var type: fullSpellWeaponTypes) {
                spellVolatilityTag.addTag(RPGSeriesItemTags.WeaponType.get(type));
            }
            // spellVolatilityTag.addTag(RPGSeriesItemTags.ArmorType.get(RPGSeriesItemTags.ArmorMetaType.MAGIC));

            /// Unbreaking enchantables
            var unbreakingTypes = Equipment.WeaponType.values();
            var unbreakingTag = valueLookupBuilder(ItemTags.DURABILITY_ENCHANTABLE);
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
            var sharpTag = valueLookupBuilder(ItemTags.SHARP_WEAPON_ENCHANTABLE);
            for (var type: sharpWeaponTypes) {
                sharpTag.addTag(RPGSeriesItemTags.WeaponType.get(type));
            }

            /// Melee enchantables
            var meleeWeaponTypes = List.of(
                    Equipment.WeaponType.SWORD, Equipment.WeaponType.CLAYMORE, Equipment.WeaponType.MACE, Equipment.WeaponType.HAMMER,
                    Equipment.WeaponType.SPEAR, Equipment.WeaponType.DAGGER, Equipment.WeaponType.SICKLE, Equipment.WeaponType.DOUBLE_AXE,
                    Equipment.WeaponType.GLAIVE
            );
            var meleeTag = valueLookupBuilder(ItemTags.SWORDS);
            for (var type: meleeWeaponTypes) {
                meleeTag.addTag(RPGSeriesItemTags.WeaponType.get(type));
            }

            /// Ranged enchantables
            var bowTypes = List.of(Equipment.WeaponType.SHORT_BOW, Equipment.WeaponType.LONG_BOW);
            for (var type: bowTypes) {
                var tag = valueLookupBuilder(ItemTags.BOW_ENCHANTABLE);
                tag.addTag(RPGSeriesItemTags.WeaponType.get(type));
            }
            var crossbowTypes = List.of(Equipment.WeaponType.RAPID_CROSSBOW, Equipment.WeaponType.HEAVY_CROSSBOW);
            for (var type: crossbowTypes) {
                var tag = valueLookupBuilder(ItemTags.CROSSBOW_ENCHANTABLE);
                tag.addTag(RPGSeriesItemTags.WeaponType.get(type));
            }

            /// Vanilla mob weapon preference (`MobEntity.getPreferredWeapons`, since 1.21.2).
            /// Mobs keep / pick up only weapons in their preferred tag, so without these a skeleton
            /// would drop an RPG Series longbow in favour of a plain vanilla bow.
            /// Tag references (not item entries) are used on purpose: content mods fill the
            /// `rpg_series:weapon_type/...` categories from their own datagen, and are covered automatically.
            var skeletonPreferred = valueLookupBuilder(ItemTags.SKELETON_PREFERRED_WEAPONS);
            for (var type: bowTypes) {
                skeletonPreferred.addTag(RPGSeriesItemTags.WeaponType.get(type));
            }
            for (var preferredTag: List.of(ItemTags.PILLAGER_PREFERRED_WEAPONS, ItemTags.PIGLIN_PREFERRED_WEAPONS)) {
                var tag = valueLookupBuilder(preferredTag);
                for (var type: crossbowTypes) {
                    tag.addTag(RPGSeriesItemTags.WeaponType.get(type));
                }
            }
            // `minecraft:drowned_preferred_weapons` is intentionally left alone: drowned prefer tridents,
            // and RPG Series has no throwable-trident weapon category to contribute.
        }
    }

    public static class WeaponSkillGen extends SpellGenerator {
        public WeaponSkillGen(FabricDataOutput dataOutput, CompletableFuture<HolderLookup.Provider> registryLookup) {
            super(dataOutput, registryLookup);
        }

        @Override
        public void generateSpells(Builder builder) {
            for (var entry: WeaponSkills.entries) {
                builder.add(entry.id(), entry.spell());
            }
        }
    }

    public static class LangGenerator extends NamespacedLangGenerator {
        public LangGenerator(FabricDataOutput dataOutput, CompletableFuture<HolderLookup.Provider> registryLookup) {
            super(dataOutput, registryLookup, "rpg_series");
        }

        @Override
        public void generateTranslations(HolderLookup.Provider wrapperLookup, TranslationBuilder translationBuilder) {
            WeaponSkills.entries.forEach(entry -> {
                var id = entry.id();
                translationBuilder.add("spell." + id.getNamespace() + "." + id.getPath() + ".name" , entry.title());
                translationBuilder.add("spell." + id.getNamespace() + "." + id.getPath() + ".description" , entry.description());
            });
        }
    }
}
