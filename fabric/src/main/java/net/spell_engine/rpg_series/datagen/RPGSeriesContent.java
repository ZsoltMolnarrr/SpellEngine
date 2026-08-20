package net.spell_engine.rpg_series.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.ItemTags;
import net.spell_engine.api.datagen.NamespacedLangGenerator;
import net.spell_engine.api.datagen.SpellGenerator;
import net.spell_engine.rpg_series.item.Equipment;
import net.spell_engine.api.tags.SpellEngineItemTags;
import net.spell_engine.rpg_series.tags.RPGSeriesItemTags;
import net.spell_power.api.SpellPowerTags;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class RPGSeriesContent {
    public static class EquipmentTagGen extends FabricTagProvider<Item> {
        public EquipmentTagGen(FabricDataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> registriesFuture) {
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
            for (var entry: RPGSeriesItemTags.ArmorType.ALL.entrySet()) {
                var tag = getOrCreateTagBuilder(entry.getValue());
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
            var spellInfinityTypes = RPGSeriesDataGen.combine(fullSpellWeaponTypes, meleeSpellWeaponTypes);
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
            var spellPowerTypes = RPGSeriesDataGen.combine(fullSpellWeaponTypes, meleeSpellWeaponTypes);
            var spellPowerTag = getOrCreateTagBuilder(SpellPowerTags.Items.Enchantable.SPELL_POWER_GENERIC);
            for (var type: spellPowerTypes) {
                spellPowerTag.addTag(RPGSeriesItemTags.WeaponType.get(type));
            }

            /// Spell Volatility enchantables
            var spellVolatilityTag = getOrCreateTagBuilder(SpellPowerTags.Items.Enchantable.CRITICAL_CHANCE);
            for (var type: fullSpellWeaponTypes) {
                spellVolatilityTag.addTag(RPGSeriesItemTags.WeaponType.get(type));
            }
            // spellVolatilityTag.addTag(RPGSeriesItemTags.ArmorType.get(RPGSeriesItemTags.ArmorMetaType.MAGIC));

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

    public static class WeaponSkillGen extends SpellGenerator {
        public WeaponSkillGen(FabricDataOutput dataOutput, CompletableFuture<RegistryWrapper.WrapperLookup> registryLookup) {
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
        public LangGenerator(FabricDataOutput dataOutput, CompletableFuture<RegistryWrapper.WrapperLookup> registryLookup) {
            super(dataOutput, registryLookup, "rpg_series");
        }

        @Override
        public void generateTranslations(RegistryWrapper.WrapperLookup wrapperLookup, TranslationBuilder translationBuilder) {
            WeaponSkills.entries.forEach(entry -> {
                var id = entry.id();
                translationBuilder.add("spell." + id.getNamespace() + "." + id.getPath() + ".name" , entry.title());
                translationBuilder.add("spell." + id.getNamespace() + "." + id.getPath() + ".description" , entry.description());
            });
        }
    }
}
