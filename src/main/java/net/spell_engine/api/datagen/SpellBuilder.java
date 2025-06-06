package net.spell_engine.api.datagen;

import net.minecraft.entity.effect.StatusEffects;
import net.spell_engine.api.spell.Spell;

import java.util.List;

public class SpellBuilder {
    private static final String GROUP_PRIMARY = "primary";

    private static Spell createSpellActive() {
        var spell = new Spell();
        spell.type = Spell.Type.ACTIVE;
        spell.active = new Spell.Active();
        spell.active.cast = new Spell.Active.Cast();
        spell.learn = new Spell.Learn();
        return spell;
    }

    private static Spell createSpellPassive() {
        var spell = new Spell();
        spell.type = Spell.Type.PASSIVE;
        spell.passive = new Spell.Passive();
        return spell;
    }

    private static Spell createSpellModifier() {
        var spell = new Spell();
        spell.type = Spell.Type.MODIFIER;
        spell.modifiers = List.of();
        return spell;
    }

    private static Spell.Impact impactDamage(float coefficient, float knockback) {
        var impact = new Spell.Impact();
        impact.action = new Spell.Impact.Action();
        impact.action.type = Spell.Impact.Action.Type.DAMAGE;
        impact.action.damage = new Spell.Impact.Action.Damage();
        impact.action.damage.spell_power_coefficient = coefficient;
        impact.action.damage.knockback = knockback;
        return impact;
    }

    private static Spell.Impact impactHeal(float spell_power_coefficient) {
        var impact = new Spell.Impact();
        impact.action.type = Spell.Impact.Action.Type.HEAL;
        impact.action.heal = new Spell.Impact.Action.Heal();
        impact.action.heal.spell_power_coefficient = spell_power_coefficient;
        return impact;
    }

    private static Spell.Impact impactEffectSet(String effectIdString, float duration, int amplifier) {
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

    private static Spell.Impact impactEffectAdd(String effectIdString, float duration, int amplifier, int amplifierCap) {
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

    private static Spell.Impact impactEffectCleanse() {
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

    private static void configureCooldown(Spell spell, float duration) {
        if (spell.cost == null) {
            spell.cost = new Spell.Cost();
        }
        if (spell.cost.cooldown == null) {
            spell.cost.cooldown = new Spell.Cost.Cooldown();
        }
        spell.cost.cooldown.duration = duration;
    }
}
