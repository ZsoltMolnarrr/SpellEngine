package net.spell_engine.fabric.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricDynamicRegistryProvider;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.util.Identifier;
import net.spell_engine.api.datagen.SpellBuilder;
import net.spell_engine.api.datagen.SpellGenerator;
import net.spell_engine.api.item.ItemAttributeModifiers;
import net.spell_engine.api.item.set.EquipmentSet;
import net.spell_engine.api.item.set.EquipmentSetRegistry;
import net.spell_engine.api.spell.ExternalSpellSchools;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.container.SpellContainerHelper;
import net.spell_engine.api.spell.container.SpellContainers;
import net.spell_engine.utils.AttributeModifierUtil;
import net.spell_power.api.SpellSchools;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class TestDataGen {

    public static final String NAMESPACE = "test";

    public static void addTo(FabricDataGenerator.Pack pack) {
        pack.addProvider(TestDataGen.TestEquipmentSetGenerator::new);
        pack.addProvider(TestDataGen.TestSpellGen::new);
    }

    public static class TestEquipmentSetGenerator extends FabricDynamicRegistryProvider {

        public TestEquipmentSetGenerator(FabricDataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> registriesFuture) {
            super(output, registriesFuture);
        }

        @Override
        protected void configure(RegistryWrapper.WrapperLookup registries, Entries entries) {
            RegistryEntryLookup<Item> itemLookup = registries.createRegistryLookup().getOrThrow(RegistryKeys.ITEM);

            var equipmentSetLookup = registries.createRegistryLookup().getOrThrow(EquipmentSetRegistry.KEY);

            var setId = RegistryKey.of(EquipmentSetRegistry.KEY, new Identifier(NAMESPACE, "fire_power"));

            var firePowerBonus = new EquipmentSet.Bonus(
                    1,
                    ItemAttributeModifiers.builder()
                            .add(SpellSchools.FIRE.attributeEntry,
                                    AttributeModifierUtil.modifier(new Identifier("fire_power_bonus"), 1, EntityAttributeModifier.Operation.ADDITION),
                                    ItemAttributeModifiers.Slot.ARMOR)
                            .build(),
                    null);
            var fireball = new EquipmentSet.Bonus(
                    2,
                    null,
                    SpellContainers.forMagicWeapon()
                            .withSpellId(new Identifier("wizards", "fireball"))
            );
            var fireProc = new EquipmentSet.Bonus(
                    3,
                    null,
                    SpellContainers.forMagicWeapon()
                            .withSpellId(new Identifier("relics_rpgs", "lesser_proc_arcane_fire"))
            );
            var explodingProc = new EquipmentSet.Bonus(
                    4,
                    null,
                    SpellContainers.forMagicWeapon()
                            .withSpellId(new Identifier("arsenal", "exploding_melee"))
            );

            var items = RegistryEntryList.of(
                    // Iron armor
                    itemLookup.getOrThrow(RegistryKey.of(RegistryKeys.ITEM, new Identifier("minecraft", "iron_helmet"))),
                    itemLookup.getOrThrow(RegistryKey.of(RegistryKeys.ITEM, new Identifier("minecraft", "iron_chestplate"))),
                    itemLookup.getOrThrow(RegistryKey.of(RegistryKeys.ITEM, new Identifier("minecraft", "iron_leggings"))),
                    itemLookup.getOrThrow(RegistryKey.of(RegistryKeys.ITEM, new Identifier("minecraft", "iron_boots")))
            );
            entries.add(setId,
                    new EquipmentSet.Definition(
                            "fire_power",
                            items,
                            List.of(firePowerBonus, fireball, fireProc, explodingProc)
                    )
            );
        }

        @Override
        public String getName() {
            return "Test EquipmentSet Generator";
        }
    }

    public static class TestSpellGen extends SpellGenerator {
        public TestSpellGen(FabricDataOutput dataOutput, CompletableFuture<RegistryWrapper.WrapperLookup> registryLookup) {
            super(dataOutput, registryLookup);
        }

        private static Spell shoutTaunt() {
            var spell = SpellBuilder.createSpellModifier();
            spell.school = ExternalSpellSchools.PHYSICAL_MELEE;
            var impact = SpellBuilder.Impacts.taunt();

            var modifier = new Spell.Modifier();
            modifier.mutate_impacts = Spell.Modifier.ImpactListModifier.APPEND;
            modifier.impacts = List.of(impact);

            spell.modifiers = List.of(modifier);
            return spell;
        }

        @Override
        public void generateSpells(Builder builder) {
            builder.add(new Identifier(NAMESPACE, "shout_taunt"), shoutTaunt());
        }
    }
}
