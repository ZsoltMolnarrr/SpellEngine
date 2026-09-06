package net.spell_engine.fabric.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import net.spell_engine.api.datagen.EquipmentSetGenerator;
import net.spell_engine.api.datagen.SpellBuilder;
import net.spell_engine.api.datagen.SpellGenerator;
import net.spell_engine.api.item.ItemAttributeModifiers;
import net.spell_engine.api.item.set.EquipmentSet;
import net.spell_engine.api.spell.ExternalSpellSchools;
import net.spell_engine.api.spell.Spell;
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

    /// Exercises {@link EquipmentSetGenerator} (SE's own datagen path for `data/<ns>/equipment_set/`) and the
    /// id-only attribute escape hatch of {@link ItemAttributeModifiers} (`ranged_weapon:damage` is written whether
    /// or not RangedWeaponAPI is on the datagen runtime).
    public static class TestEquipmentSetGenerator extends EquipmentSetGenerator {

        public TestEquipmentSetGenerator(FabricDataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> registriesFuture) {
            super(output, registriesFuture);
        }

        @Override
        public void generateEquipmentSets(Builder builder) {
            var firePowerBonus = new EquipmentSet.Bonus(
                    1,
                    ItemAttributeModifiers.builder()
                            .add(SpellSchools.FIRE.attributeEntry,
                                    AttributeModifierUtil.modifier(new Identifier("fire_power_bonus"), 1, EntityAttributeModifier.Operation.ADDITION),
                                    ItemAttributeModifiers.Slot.ARMOR)
                            // Optional-mod attribute by id: serializes without RWA present
                            .add(new Identifier("ranged_weapon", "damage"),
                                    AttributeModifierUtil.modifier(new Identifier("ranged_damage_bonus"), 0.05, EntityAttributeModifier.Operation.MULTIPLY_BASE),
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

            builder.add(new Identifier(NAMESPACE, "fire_power"), "fire_power",
                    List.of(
                            new Identifier("minecraft", "iron_helmet"),
                            new Identifier("minecraft", "iron_chestplate"),
                            new Identifier("minecraft", "iron_leggings"),
                            new Identifier("minecraft", "iron_boots")
                    ),
                    List.of(firePowerBonus, fireball, fireProc, explodingProc));
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

        /// Distinct from `RPGSeriesContent.WeaponSkillGen`'s "Spell Generator" (Fabric rejects duplicate provider names)
        @Override
        public String getName() {
            return "Test Spell Generator";
        }
    }
}
