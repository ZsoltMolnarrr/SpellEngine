package net.spell_engine.api.spell;

import net.minecraft.registry.tag.EntityTypeTags;
import net.minecraft.util.Identifier;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.tags.SpellEngineEntityTags;
import net.spell_engine.config.WeaknessConfig;
import net.spell_power.api.SpellSchool;
import net.spell_power.api.SpellSchools;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SpellSchoolWeakness {
    public static List<Spell.Impact.TargetModifier> getWeaknesses(Identifier schoolId) {
        if (schoolId == null) {
            return List.of();
        }
        var config = SpellEngineMod.weaknessConfig.value;
        if (config == null || config.school_weaknesses == null) {
            return List.of();
        }
        var key = schoolId.toString();
        return config.school_weaknesses.getOrDefault(key, List.of());
    }

    public static List<Spell.Impact.TargetModifier> getWeaknesses(@Nullable SpellSchool school) {
        if (school == null) {
            return List.of();
        }
        return getWeaknesses(school.id);
    }

    public static WeaknessConfig createDefault() {
        var config = new WeaknessConfig();

        var fireWeakness = new Spell.Impact.TargetModifier();
        var fireCondition = new Spell.TargetCondition();

        fireCondition.entity_type = "#" + SpellEngineEntityTags.Vulnerability.WEAK_TO_FIRE.id();
        fireWeakness.conditions = List.of(fireCondition);
        fireWeakness.modifier = new Spell.Impact.Modifier();
        fireWeakness.modifier.critical_chance_bonus = 0.3f;
        config.school_weaknesses.put(SpellSchools.FIRE.id.toString(), List.of(fireWeakness));

        var frostWeakness = new Spell.Impact.TargetModifier();
        var frostWeaknessCondition = new Spell.TargetCondition();
        frostWeaknessCondition.entity_type = "#" + SpellEngineEntityTags.Vulnerability.WEAK_TO_FROST.id();
        frostWeakness.conditions = List.of(frostWeaknessCondition);
        frostWeakness.modifier = new Spell.Impact.Modifier();
        frostWeakness.modifier.power_multiplier = 0.3f;

        var frostResistance = new Spell.Impact.TargetModifier();
        var frostResistanceCondition = new Spell.TargetCondition();
        frostResistanceCondition.entity_type = "#" + SpellEngineEntityTags.Vulnerability.RESISTANT_TO_FROST.id();
        frostResistance.conditions = List.of(frostResistanceCondition);
        frostResistance.modifier = new Spell.Impact.Modifier();
        frostResistance.modifier.power_multiplier = -0.3f;

        config.school_weaknesses.put(SpellSchools.FROST.id.toString(), List.of(frostWeakness, frostResistance));

        return config;
    }
}
