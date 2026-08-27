package net.spell_engine.fabric.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricDynamicRegistryProvider;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.spell_engine.api.datagen.SpellBuilder;
import net.spell_engine.api.datagen.SpellGenerator;
import net.spell_engine.api.item.set.EquipmentSet;
import net.spell_engine.api.item.set.EquipmentSetRegistry;
import net.spell_engine.api.spell.ExternalSpellSchools;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.container.SpellContainerHelper;
import net.spell_engine.api.spell.container.SpellContainers;
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

        public TestEquipmentSetGenerator(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
            super(output, registriesFuture);
        }

        @Override
        protected void configure(HolderLookup.Provider registries, Entries entries) {
            HolderGetter<Item> itemLookup = registries.lookupOrThrow(Registries.ITEM);

            var equipmentSetLookup = registries.lookupOrThrow(EquipmentSetRegistry.KEY);

            var setId = ResourceKey.create(EquipmentSetRegistry.KEY, Identifier.fromNamespaceAndPath(NAMESPACE, "fire_power"));

            var firePowerBonus = new EquipmentSet.Bonus(
                    1,
                    new ItemAttributeModifiers(
                            List.of(
                                    new ItemAttributeModifiers.Entry(
                                            SpellSchools.FIRE.attributeEntry,
                                            new AttributeModifier(
                                                    Identifier.parse("fire_power_bonus"),
                                                    1,
                                                    AttributeModifier.Operation.ADD_VALUE
                                            ),
                                            EquipmentSlotGroup.ARMOR)
                            )
                    ),
                    null);
            var fireball = new EquipmentSet.Bonus(
                    2,
                    null,
                    SpellContainers.forMagicWeapon()
                            .withSpellId(Identifier.fromNamespaceAndPath("wizards", "fireball"))
            );
            var fireProc = new EquipmentSet.Bonus(
                    3,
                    null,
                    SpellContainers.forMagicWeapon()
                            .withSpellId(Identifier.fromNamespaceAndPath("relics_rpgs", "lesser_proc_arcane_fire"))
            );
            var explodingProc = new EquipmentSet.Bonus(
                    4,
                    null,
                    SpellContainers.forMagicWeapon()
                            .withSpellId(Identifier.fromNamespaceAndPath("arsenal", "exploding_melee"))
            );

            var items = HolderSet.direct(
                    // Iron armor
                    itemLookup.getOrThrow(ResourceKey.create(Registries.ITEM, Identifier.withDefaultNamespace("iron_helmet"))),
                    itemLookup.getOrThrow(ResourceKey.create(Registries.ITEM, Identifier.withDefaultNamespace("iron_chestplate"))),
                    itemLookup.getOrThrow(ResourceKey.create(Registries.ITEM, Identifier.withDefaultNamespace("iron_leggings"))),
                    itemLookup.getOrThrow(ResourceKey.create(Registries.ITEM, Identifier.withDefaultNamespace("iron_boots")))
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
        public TestSpellGen(FabricPackOutput dataOutput, CompletableFuture<HolderLookup.Provider> registryLookup) {
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
            builder.add(Identifier.fromNamespaceAndPath(NAMESPACE, "shout_taunt"), shoutTaunt());
        }
    }
}
