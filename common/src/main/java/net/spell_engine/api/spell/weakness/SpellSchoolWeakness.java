package net.spell_engine.api.spell.weakness;

import net.minecraft.resources.Identifier;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.tags.SpellEngineEntityTags;
import net.spell_engine.api.util.TriState;
import net.spell_engine.config.WeaknessConfig;
import net.spell_power.api.SpellSchool;
import net.spell_power.api.SpellSchools;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SpellSchoolWeakness {
    public static List<ScopedWeakness> getWeaknesses(Identifier schoolId) {
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

    public static List<ScopedWeakness> getWeaknesses(@Nullable SpellSchool school) {
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
        config.school_weaknesses.put(SpellSchools.FIRE.id.toString(), List.of(
                new ScopedWeakness(Spell.Impact.Action.Type.DAMAGE, fireWeakness)
        ));

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
        config.school_weaknesses.put(SpellSchools.FROST.id.toString(), List.of(
                new ScopedWeakness(null, frostWeakness),
                new ScopedWeakness(null, frostResistance)
        ));

        // Healing school: Cannot heal mechanical entities, +100% crit vs undead
        var healingDenyMechanical = new Spell.Impact.TargetModifier();
        var healingMechanicalCondition = new Spell.TargetCondition();
        healingMechanicalCondition.entity_type = "#" + SpellEngineEntityTags.mechanical.location();
        healingDenyMechanical.conditions = List.of(healingMechanicalCondition);
        healingDenyMechanical.execute = TriState.DENY;

        var healingUndeadWeakness = new Spell.Impact.TargetModifier();
        var healingUndeadCondition = new Spell.TargetCondition();
        healingUndeadCondition.entity_type = "#" + SpellEngineEntityTags.Vulnerability.WEAK_TO_HOLY.id();
        healingUndeadWeakness.conditions = List.of(healingUndeadCondition);
        healingUndeadWeakness.modifier = new Spell.Impact.Modifier();
        healingUndeadWeakness.modifier.critical_chance_bonus = 1.0f;

        config.school_weaknesses.put(SpellSchools.HEALING.id.toString(), List.of(
                new ScopedWeakness(Spell.Impact.Action.Type.HEAL, healingDenyMechanical),
                new ScopedWeakness(Spell.Impact.Action.Type.DAMAGE, healingUndeadWeakness)
        ));

        return config;
    }
}
