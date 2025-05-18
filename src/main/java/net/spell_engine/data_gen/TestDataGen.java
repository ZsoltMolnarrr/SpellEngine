package net.spell_engine.data_gen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricDynamicRegistryProvider;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.util.Identifier;
import net.spell_engine.api.item.set.EquipmentSet;
import net.spell_engine.api.item.set.EquipmentSetRegistry;
import net.spell_power.api.SpellSchools;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class TestDataGen {

    public static final String NAMESPACE = "test";

    public static class TestEquipmentSetGenerator extends FabricDynamicRegistryProvider {

        public TestEquipmentSetGenerator(FabricDataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> registriesFuture) {
            super(output, registriesFuture);
        }

        @Override
        protected void configure(RegistryWrapper.WrapperLookup registries, Entries entries) {
            RegistryEntryLookup<Item> itemLookup = registries.createRegistryLookup().getOrThrow(RegistryKeys.ITEM);

            var equipmentSetLookup = registries.createRegistryLookup().getOrThrow(EquipmentSetRegistry.KEY);

            var setId = RegistryKey.of(EquipmentSetRegistry.KEY, Identifier.of(NAMESPACE, "fire_power"));

            var firePowerBonus = new EquipmentSet.Bonus(
                    2,
                    new AttributeModifiersComponent(
                            List.of(
                                    new AttributeModifiersComponent.Entry(
                                            SpellSchools.FIRE.attributeEntry,
                                            new EntityAttributeModifier(
                                                    Identifier.of("fire_power_bonus"),
                                                    1,
                                                    EntityAttributeModifier.Operation.ADD_VALUE
                                            ),
                                            AttributeModifierSlot.ARMOR)
                            ),
                            true
                    ),
                    null);
            var items = RegistryEntryList.of(
//                    itemLookup.getOrThrow(RegistryKey.of(RegistryKeys.ITEM, Identifier.of("wizards", "fire_robe_head"))),
//                    itemLookup.getOrThrow(RegistryKey.of(RegistryKeys.ITEM, Identifier.of("wizards", "fire_robe_chest"))),
//                    itemLookup.getOrThrow(RegistryKey.of(RegistryKeys.ITEM, Identifier.of("wizards", "fire_robe_legs"))),
//                    itemLookup.getOrThrow(RegistryKey.of(RegistryKeys.ITEM, Identifier.of("wizards", "fire_robe_feet")))

                    // Iron armor
                    itemLookup.getOrThrow(RegistryKey.of(RegistryKeys.ITEM, Identifier.ofVanilla("iron_helmet"))),
                    itemLookup.getOrThrow(RegistryKey.of(RegistryKeys.ITEM, Identifier.ofVanilla("iron_chestplate"))),
                    itemLookup.getOrThrow(RegistryKey.of(RegistryKeys.ITEM, Identifier.ofVanilla("iron_leggings"))),
                    itemLookup.getOrThrow(RegistryKey.of(RegistryKeys.ITEM, Identifier.ofVanilla("iron_boots")))
            );
            entries.add(setId,
                    new EquipmentSet.Definition(
                            "fire_power",
                            items,
                            List.of(firePowerBonus)
                    )
            );
        }

        @Override
        public String getName() {
            return "TestEquipmentSetGenerator";
        }
    }
}
