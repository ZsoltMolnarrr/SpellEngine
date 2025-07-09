package net.spell_engine.api.datagen;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.Identifier;
import net.spell_engine.api.entity.SpellEntityPredicates;
import net.spell_engine.api.spell.ExternalSpellSchools;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.fx.ParticleBatch;
import net.spell_engine.client.util.Color;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class SpellBuilder {
    public static final String GROUP_PRIMARY = "primary";

    public static Spell createSpellActive() {
        var spell = new Spell();
        spell.type = Spell.Type.ACTIVE;
        spell.active = new Spell.Active();
        spell.active.cast = new Spell.Active.Cast();
        spell.learn = new Spell.Learn();
        return spell;
    }

    public static Spell createSpellPassive() {
        var spell = new Spell();
        spell.type = Spell.Type.PASSIVE;
        spell.passive = new Spell.Passive();
        return spell;
    }

    public static Spell createSpellModifier() {
        var spell = new Spell();
        spell.type = Spell.Type.MODIFIER;
        spell.range = 0;
        spell.modifiers = new ArrayList<>();
        return spell;
    }

    public static Spell.TargetCondition targetConditionDead() {
        var deadCondition = new Spell.TargetCondition();
        deadCondition.health_percent_below = 0F;
        deadCondition.health_percent_above = 0F;
        return deadCondition;
    }

    public static Spell.TargetCondition targetConditionWeak() {
        var deadCondition = new Spell.TargetCondition();
        deadCondition.health_percent_below = 0.5F;
        deadCondition.health_percent_above = 0.01F;
        return deadCondition;
    }

    public static Spell.TargetCondition targetConditionHasEffect(Identifier effectId) {
        var effectCondition = new Spell.TargetCondition();
        effectCondition.entity_predicate_id = SpellEntityPredicates.HAS_EFFECT.id().toString();
        effectCondition.entity_predicate_param = effectId.toString();
        return effectCondition;
    }

    public static Spell.Trigger triggerActiveSpellHit(float chance, @Nullable String schoolRegex) {
        var trigger = new Spell.Trigger();
        trigger.impact = new Spell.Trigger.ImpactCondition();
        trigger.impact.impact_type = Spell.Impact.Action.Type.DAMAGE.toString();
        trigger.type = Spell.Trigger.Type.SPELL_IMPACT_SPECIFIC;
        trigger.spell = new Spell.Trigger.SpellCondition();
        trigger.spell.school = schoolRegex;
        trigger.spell.type = Spell.Type.ACTIVE;
        trigger.chance = chance;
        return trigger;
    }

    public static Spell.Trigger triggerSpecificSpellHit(String spellId) {
        var trigger = new Spell.Trigger();
        trigger.impact = new Spell.Trigger.ImpactCondition();
        trigger.impact.impact_type = Spell.Impact.Action.Type.DAMAGE.toString();
        trigger.type = Spell.Trigger.Type.SPELL_IMPACT_SPECIFIC;
        trigger.spell = new Spell.Trigger.SpellCondition();
        trigger.spell.id = spellId;
        return trigger;
    }

    public static Spell.Trigger triggerActiveSpellHeal(float chance) {
        var trigger = new Spell.Trigger();
        trigger.impact = new Spell.Trigger.ImpactCondition();
        trigger.impact.impact_type = Spell.Impact.Action.Type.HEAL.toString();
        trigger.type = Spell.Trigger.Type.SPELL_IMPACT_SPECIFIC;
        trigger.spell = new Spell.Trigger.SpellCondition();
        trigger.spell.type = Spell.Type.ACTIVE;
        trigger.chance = chance;
        return trigger;
    }

    public static Spell.Trigger triggerMeleeAttack(boolean mustWield) {
        var trigger = new Spell.Trigger();
        trigger.type = Spell.Trigger.Type.MELEE_IMPACT;
        if (mustWield) {
            trigger.equipment_condition = EquipmentSlot.MAINHAND;
        }
        return trigger;
    }

    public static Spell.Trigger triggerMeleeKill(boolean mustWield) {
        var trigger = triggerMeleeAttack(mustWield);
        var deadCondition = targetConditionDead();
        trigger.target_conditions = List.of(deadCondition);
        return trigger;
    }

    public static Spell.Trigger triggerSpellKill() {
        var trigger = new Spell.Trigger();
        trigger.type = Spell.Trigger.Type.SPELL_IMPACT_SPECIFIC;
        trigger.impact = new Spell.Trigger.ImpactCondition();
        trigger.impact.impact_type = Spell.Impact.Action.Type.DAMAGE.toString();
        var deadCondition = targetConditionDead();
        trigger.target_conditions = List.of(deadCondition);
        return trigger;
    }

    public static List<Spell.Trigger> triggerRangedKill() {
        var deadCondition = targetConditionDead();

        var arrowTrigger = new Spell.Trigger();
        arrowTrigger.type = Spell.Trigger.Type.ARROW_IMPACT;
        arrowTrigger.equipment_condition = EquipmentSlot.MAINHAND;
        arrowTrigger.target_conditions = List.of(deadCondition);

        var skillTrigger = new Spell.Trigger();
        skillTrigger.type = Spell.Trigger.Type.SPELL_IMPACT_SPECIFIC;
        skillTrigger.spell = new Spell.Trigger.SpellCondition();
        skillTrigger.spell.school = ExternalSpellSchools.PHYSICAL_RANGED.id.toString();
        skillTrigger.target_conditions = List.of(deadCondition);

        return List.of(arrowTrigger, skillTrigger);
    }

    public static Spell.Trigger triggerShieldBlock() {
        var trigger = new Spell.Trigger();
        trigger.type = Spell.Trigger.Type.SHIELD_BLOCK;
        return trigger;
    }

    public static Spell.Trigger triggerArrowHit() {
        var trigger = new Spell.Trigger();
        trigger.type = Spell.Trigger.Type.ARROW_IMPACT;
        return trigger;
    }

    public static Spell.Impact impactDamage(float coefficient, float knockback) {
        var impact = new Spell.Impact();
        impact.action = new Spell.Impact.Action();
        impact.action.type = Spell.Impact.Action.Type.DAMAGE;
        impact.action.damage = new Spell.Impact.Action.Damage();
        impact.action.damage.spell_power_coefficient = coefficient;
        impact.action.damage.knockback = knockback;
        return impact;
    }

    public static Spell.Impact impactHeal(float spell_power_coefficient) {
        var impact = new Spell.Impact();
        impact.action = new Spell.Impact.Action();
        impact.action.type = Spell.Impact.Action.Type.HEAL;
        impact.action.heal = new Spell.Impact.Action.Heal();
        impact.action.heal.spell_power_coefficient = spell_power_coefficient;
        return impact;
    }

    public static Spell.Impact impactEffectSet(String effectIdString, float duration, int amplifier) {
        var impact = new Spell.Impact();
        impact.action = new Spell.Impact.Action();
        impact.action.type = Spell.Impact.Action.Type.STATUS_EFFECT;
        impact.action.status_effect = new Spell.Impact.Action.StatusEffect();
        impact.action.status_effect.apply_mode = Spell.Impact.Action.StatusEffect.ApplyMode.SET;
        impact.action.status_effect.effect_id = effectIdString;
        impact.action.status_effect.duration = duration;
        impact.action.status_effect.amplifier = amplifier;
        return impact;
    }

    public static Spell.Impact impactEffectAdd(String effectIdString, float duration, int amplifier, int amplifierCap) {
        var impact = new Spell.Impact();
        impact.action = new Spell.Impact.Action();
        impact.action.type = Spell.Impact.Action.Type.STATUS_EFFECT;
        impact.action.status_effect = new Spell.Impact.Action.StatusEffect();
        impact.action.status_effect.apply_mode = Spell.Impact.Action.StatusEffect.ApplyMode.ADD;
        impact.action.status_effect.effect_id = effectIdString;
        impact.action.status_effect.duration = duration;
        impact.action.status_effect.amplifier = amplifier;
        impact.action.status_effect.amplifier_cap = amplifierCap;
        return impact;
    }

    public static Spell.Impact impactEffectCleanse() {
        var cleanse = new Spell.Impact();
        cleanse.action = new Spell.Impact.Action();
        cleanse.action.type = Spell.Impact.Action.Type.STATUS_EFFECT;
        cleanse.action.status_effect = new Spell.Impact.Action.StatusEffect();
        cleanse.action.status_effect.apply_mode = Spell.Impact.Action.StatusEffect.ApplyMode.REMOVE;
        cleanse.action.status_effect.remove = new Spell.Impact.Action.StatusEffect.Remove();
        cleanse.action.status_effect.remove.id = "!" + StatusEffects.TRIAL_OMEN.getIdAsString();
        cleanse.action.status_effect.remove.selector = Spell.Impact.Action.StatusEffect.Remove.Selector.RANDOM;
        cleanse.action.status_effect.remove.select_beneficial = false;
        return cleanse;
    }

    public static Spell.Impact impactTaunt() {
        var taunt = new Spell.Impact();
        taunt.action = new Spell.Impact.Action();
        taunt.action.type = Spell.Impact.Action.Type.TAUNT;
        taunt.action.taunt = new Spell.Impact.Action.Taunt();
        return taunt;
    }

    public static Spell.Impact impactFire(float duration) {
        var fire = new Spell.Impact();
        fire.action = new Spell.Impact.Action();
        fire.action.type = Spell.Impact.Action.Type.FIRE;
        fire.action.fire = new Spell.Impact.Action.Fire();
        fire.action.fire.duration = duration;
        return fire;
    }

    public static void configureCooldown(Spell spell, float duration) {
        if (spell.cost == null) {
            spell.cost = new Spell.Cost();
        }
        if (spell.cost.cooldown == null) {
            spell.cost.cooldown = new Spell.Cost.Cooldown();
        }
        spell.cost.cooldown.duration = duration;
    }

    public static class Particles {
        public static ParticleBatch popUpSign(Identifier signId, Color color) {
            return new ParticleBatch(signId.toString(),
                    ParticleBatch.Shape.LINE_VERTICAL, ParticleBatch.Origin.CENTER,
                    1, 0.75F, 0.75F)
                    .scale(0.8F)
                    .color(color.toRGBA())
                    .followEntity(true);
        }
    }
}
