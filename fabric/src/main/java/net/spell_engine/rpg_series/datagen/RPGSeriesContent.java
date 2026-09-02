package net.spell_engine.rpg_series.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.tags.ItemTags;
import net.minecraft.references.ItemIds;
import net.spell_engine.api.datagen.NamespacedLangGenerator;
import net.spell_engine.api.datagen.SpellGenerator;
import net.spell_engine.rpg_series.item.Equipment;
import net.spell_engine.api.tags.SpellEngineItemTags;
import net.spell_engine.rpg_series.tags.RPGSeriesItemTags;
import net.spell_power.api.SpellPowerTags;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class RPGSeriesContent {
    /// 26.2: `valueLookupBuilder` is gone; `builder(tag)` hands a `BlockItemTagAppender` that takes `ItemIds` keys
    public static class EquipmentTagGen extends FabricTagsProvider.ItemTagsProvider {
        public EquipmentTagGen(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
            super(output, registriesFuture);
        }

        /// Vanilla gear classified into RPG Series loot tiers. Loot tables dropping these get the
        /// matching `loot_tier` items injected by the loot fallback injector.
        private void generateLootReferenceTags() {
            var WEAPONS = RPGSeriesItemTags.LootCategory.WEAPONS;
            var ARMORS = RPGSeriesItemTags.LootCategory.ARMORS;
            for (int i = 0; i < RPGSeriesItemTags.LootTiers.DEFAULT_TIERS; i++) {
                builder(RPGSeriesItemTags.LootReference.get(i, WEAPONS));
                builder(RPGSeriesItemTags.LootReference.get(i, ARMORS));
                builder(RPGSeriesItemTags.LootReference.treasures(i));
            }
            // Treasure signals: valuables that only show up in loot, used for accessories / relics
            builder(RPGSeriesItemTags.LootReference.treasures(1))
                    .add(ItemIds.EMERALD, ItemIds.ENCHANTED_BOOK, ItemIds.GOLDEN_APPLE);
            builder(RPGSeriesItemTags.LootReference.treasures(2))
                    .add(ItemIds.DIAMOND, ItemIds.HEART_OF_THE_SEA, ItemIds.ENCHANTED_GOLDEN_APPLE, ItemIds.DIAMOND_HORSE_ARMOR,
                         ItemIds.MUSIC_DISC_13, ItemIds.MUSIC_DISC_CAT, ItemIds.MUSIC_DISC_OTHERSIDE,
                         ItemIds.MUSIC_DISC_PIGSTEP, ItemIds.MUSIC_DISC_RELIC, ItemIds.MUSIC_DISC_5);
            builder(RPGSeriesItemTags.LootReference.treasures(3))
                    .add(ItemIds.TOTEM_OF_UNDYING, ItemIds.NETHERITE_INGOT, ItemIds.NETHERITE_SCRAP,
                         ItemIds.NETHER_STAR, ItemIds.ELYTRA);
            builder(RPGSeriesItemTags.LootReference.get(0, WEAPONS))
                    .add(ItemIds.WOODEN_SWORD, ItemIds.WOODEN_AXE,
                         ItemIds.STONE_SWORD, ItemIds.STONE_AXE);
            builder(RPGSeriesItemTags.LootReference.get(RPGSeriesItemTags.LootReference.GOLDEN_WEAPONS))
                    .add(ItemIds.GOLDEN_SWORD, ItemIds.GOLDEN_AXE);
            builder(RPGSeriesItemTags.LootReference.get(0, ARMORS))
                    .add(ItemIds.LEATHER_HELMET, ItemIds.LEATHER_CHESTPLATE, ItemIds.LEATHER_LEGGINGS, ItemIds.LEATHER_BOOTS,
                         ItemIds.GOLDEN_HELMET, ItemIds.GOLDEN_CHESTPLATE, ItemIds.GOLDEN_LEGGINGS, ItemIds.GOLDEN_BOOTS);
            builder(RPGSeriesItemTags.LootReference.get(1, WEAPONS))
                    .add(ItemIds.IRON_SWORD, ItemIds.IRON_AXE, ItemIds.BOW, ItemIds.CROSSBOW);
            builder(RPGSeriesItemTags.LootReference.get(1, ARMORS))
                    .add(ItemIds.IRON_HELMET, ItemIds.IRON_CHESTPLATE, ItemIds.IRON_LEGGINGS, ItemIds.IRON_BOOTS,
                         ItemIds.CHAINMAIL_HELMET, ItemIds.CHAINMAIL_CHESTPLATE, ItemIds.CHAINMAIL_LEGGINGS, ItemIds.CHAINMAIL_BOOTS,
                         ItemIds.TURTLE_HELMET);
            builder(RPGSeriesItemTags.LootReference.get(2, WEAPONS))
                    .add(ItemIds.DIAMOND_SWORD, ItemIds.DIAMOND_AXE, ItemIds.TRIDENT);
            builder(RPGSeriesItemTags.LootReference.get(2, ARMORS))
                    .add(ItemIds.DIAMOND_HELMET, ItemIds.DIAMOND_CHESTPLATE, ItemIds.DIAMOND_LEGGINGS, ItemIds.DIAMOND_BOOTS);
            builder(RPGSeriesItemTags.LootReference.get(3, WEAPONS))
                    .add(ItemIds.NETHERITE_SWORD, ItemIds.NETHERITE_AXE, ItemIds.MACE);
            builder(RPGSeriesItemTags.LootReference.get(3, ARMORS))
                    .add(ItemIds.NETHERITE_HELMET, ItemIds.NETHERITE_CHESTPLATE, ItemIds.NETHERITE_LEGGINGS, ItemIds.NETHERITE_BOOTS);
        }

        @Override
        protected void addTags(HolderLookup.Provider wrapperLookup) {
            for (var entry: RPGSeriesItemTags.WeaponType.ALL.entrySet()) {
                var tag = builder(entry.getValue());
            }
            for (var archetype: RPGSeriesItemTags.RoleArchetype.values()) {
                var tag = builder(RPGSeriesItemTags.Archetype.tag(archetype));
                for (var entry: RPGSeriesItemTags.WeaponType.ALL.entrySet()) {
                    if (RPGSeriesItemTags.Archetype.classify(entry.getKey()) == archetype) {
                        tag.addTag(entry.getValue());
                    }
                }
            }
            for (var entry: RPGSeriesItemTags.LootThemes.ALL.entrySet()) {
                var tag = builder(entry.getValue());
            }
            for (int i = 0; i < RPGSeriesItemTags.LootTiers.DEFAULT_TIERS; i++) {
                for (var category: RPGSeriesItemTags.LootCategory.values()) {
                    var tag = builder(RPGSeriesItemTags.LootTiers.get(i, category));
                }
            }
            for (var entry: RPGSeriesItemTags.ArmorType.ALL.entrySet()) {
                var tag = builder(entry.getValue());
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
            var spellInfinityTag = builder(SpellEngineItemTags.ENCHANTABLE_SPELL_INFINITY);
            for (var type: spellInfinityTypes) {
                spellInfinityTag.addTag(RPGSeriesItemTags.WeaponType.get(type));
            }

            /// Spell Haste enchantables
            var spellHasteTag = builder(SpellPowerTags.Items.Enchantable.HASTE);
            for (var type: fullSpellWeaponTypes) {
                spellHasteTag.addTag(RPGSeriesItemTags.WeaponType.get(type));
            }

            /// Amplify Spell enchantables
            var criticalDamageTag = builder(SpellPowerTags.Items.Enchantable.CRITICAL_DAMAGE);
            for (var type: fullSpellWeaponTypes) {
                criticalDamageTag.addTag(RPGSeriesItemTags.WeaponType.get(type));
            }

            /// Spell Power enchantables
            var spellPowerTypes = RPGSeriesDataGen.combine(fullSpellWeaponTypes, meleeSpellWeaponTypes);
            var spellPowerTag = builder(SpellPowerTags.Items.Enchantable.SPELL_POWER_GENERIC);
            for (var type: spellPowerTypes) {
                spellPowerTag.addTag(RPGSeriesItemTags.WeaponType.get(type));
            }

            /// Spell Volatility enchantables
            var spellVolatilityTag = builder(SpellPowerTags.Items.Enchantable.CRITICAL_CHANCE);
            for (var type: fullSpellWeaponTypes) {
                spellVolatilityTag.addTag(RPGSeriesItemTags.WeaponType.get(type));
            }
            // spellVolatilityTag.addTag(RPGSeriesItemTags.ArmorType.get(RPGSeriesItemTags.ArmorMetaType.MAGIC));

            /// Unbreaking enchantables
            var unbreakingTypes = Equipment.WeaponType.values();
            var unbreakingTag = builder(ItemTags.DURABILITY_ENCHANTABLE);
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
            var sharpTag = builder(ItemTags.SHARP_WEAPON_ENCHANTABLE);
            for (var type: sharpWeaponTypes) {
                sharpTag.addTag(RPGSeriesItemTags.WeaponType.get(type));
            }

            /// Melee enchantables
            var meleeWeaponTypes = List.of(
                    Equipment.WeaponType.SWORD, Equipment.WeaponType.CLAYMORE, Equipment.WeaponType.MACE, Equipment.WeaponType.HAMMER,
                    Equipment.WeaponType.SPEAR, Equipment.WeaponType.DAGGER, Equipment.WeaponType.SICKLE, Equipment.WeaponType.DOUBLE_AXE,
                    Equipment.WeaponType.GLAIVE
            );
            var meleeTag = builder(ItemTags.SWORDS);
            for (var type: meleeWeaponTypes) {
                meleeTag.addTag(RPGSeriesItemTags.WeaponType.get(type));
            }

            /// Ranged enchantables
            var bowTypes = List.of(Equipment.WeaponType.SHORT_BOW, Equipment.WeaponType.LONG_BOW);
            for (var type: bowTypes) {
                var tag = builder(ItemTags.BOW_ENCHANTABLE);
                tag.addTag(RPGSeriesItemTags.WeaponType.get(type));
            }
            var crossbowTypes = List.of(Equipment.WeaponType.RAPID_CROSSBOW, Equipment.WeaponType.HEAVY_CROSSBOW);
            for (var type: crossbowTypes) {
                var tag = builder(ItemTags.CROSSBOW_ENCHANTABLE);
                tag.addTag(RPGSeriesItemTags.WeaponType.get(type));
            }

            /// Vanilla mob weapon preference (`MobEntity.getPreferredWeapons`, since 1.21.2).
            /// Mobs keep / pick up only weapons in their preferred tag, so without these a skeleton
            /// would drop an RPG Series longbow in favour of a plain vanilla bow.
            /// Tag references (not item entries) are used on purpose: content mods fill the
            /// `rpg_series:weapon_type/...` categories from their own datagen, and are covered automatically.
            var skeletonPreferred = builder(ItemTags.SKELETON_PREFERRED_WEAPONS);
            for (var type: bowTypes) {
                skeletonPreferred.addTag(RPGSeriesItemTags.WeaponType.get(type));
            }
            for (var preferredTag: List.of(ItemTags.PILLAGER_PREFERRED_WEAPONS, ItemTags.PIGLIN_PREFERRED_WEAPONS)) {
                var tag = builder(preferredTag);
                for (var type: crossbowTypes) {
                    tag.addTag(RPGSeriesItemTags.WeaponType.get(type));
                }
            }
            // `minecraft:drowned_preferred_weapons` is intentionally left alone: drowned prefer tridents,
            // and RPG Series has no throwable-trident weapon category to contribute.
        }
    }

    public static class WeaponSkillGen extends SpellGenerator {
        public WeaponSkillGen(FabricPackOutput dataOutput, CompletableFuture<HolderLookup.Provider> registryLookup) {
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
        public LangGenerator(FabricPackOutput dataOutput, CompletableFuture<HolderLookup.Provider> registryLookup) {
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
