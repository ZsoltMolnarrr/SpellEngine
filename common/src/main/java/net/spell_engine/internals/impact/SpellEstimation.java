package net.spell_engine.internals.impact;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.internals.SpellExecution;
import net.spell_engine.internals.SpellParameters;
import net.spell_engine.utils.AttributeModifierUtil;
import net.spell_power.api.SpellPower;

import java.util.ArrayList;
import java.util.List;

/// Damage and heal output estimation, as shown in spell tooltips. Reads the same power resolution
/// the live impacts do ({@link SpellExecution.Power}), so the promised range and the delivered hit
/// stay in agreement.
public class SpellEstimation {

    public static EstimatedOutput estimate(Spell spell, PlayerEntity caster, ItemStack itemStack) {
        var spellSchool = spell.school;
        var damageEffects = new ArrayList<EstimatedValue>();
        var healEffects = new ArrayList<EstimatedValue>();
        var isEquipped = AttributeModifierUtil.isItemStackEquipped(itemStack, caster);
        // CHARGE casts: base impact values represent a full charge, so extend the displayed
        // minimum down to the output of the weakest allowed release.
        var chargeMinOutput = 1F;
        var charge = SpellParameters.chargeConfigOf(spell);
        if (charge != null) {
            chargeMinOutput = SpellParameters.chargeOutputMultiplier(spell, charge.curve.apply(charge.min_release_ratio));
        }
        ArrayList<Spell.Impact> impacts = new ArrayList<>(spell.impacts);
        if (spell.modifiers != null) {
            for (var modifier : spell.modifiers) {
                impacts.addAll(modifier.impacts);
            }
        }

        for (var impact: impacts) {
            var school = impact.school != null ? impact.school : spellSchool;
            var attribute = school.attributeEntry;
            if (impact.attribute != null && !impact.attribute.isEmpty()) {
                var optionalAttribute = Registries.ATTRIBUTE.getEntry(Identifier.of(impact.attribute));
                if (optionalAttribute.isPresent()) {
                    attribute = optionalAttribute.get();
                }
            }

            var flatBonusOnItemStack = AttributeModifierUtil.flatBonusFrom(itemStack, attribute);
            /// It would be best to have some information here about the context
            /// whether the spell tooltip is generated for a cache, or for a player initiated tooltip
            boolean useRealAttributes = isEquipped || flatBonusOnItemStack == 0;

            SpellPower.Result power;
            if (useRealAttributes) {
                power = SpellExecution.Power.resolve(spell, impact, caster, null, null);
            } else {
                power = new SpellPower.Result(school, flatBonusOnItemStack, 0, 1F);
            }
            power = SpellExecution.Power.clamped(power, impact.action);

            switch (impact.action.type) {
                case DAMAGE -> {
                    var damageData = impact.action.damage;
                    var damage = new EstimatedValue(power.nonCriticalValue() * chargeMinOutput, power.forcedCriticalValue())
                            .multiply(damageData.spell_power_coefficient);
                    damageEffects.add(damage);
                }
                case HEAL -> {
                    var healData = impact.action.heal;
                    var healing = new EstimatedValue(power.nonCriticalValue() * chargeMinOutput, power.forcedCriticalValue())
                            .multiply(healData.spell_power_coefficient);
                    healEffects.add(healing);
                }
            }
        }

        return new EstimatedOutput(damageEffects, healEffects);
    }

    public record EstimatedValue(double min, double max) {
        public EstimatedValue multiply(double value) {
            return new EstimatedValue(min * value, max * value);
        }
    }
    public record EstimatedOutput(List<EstimatedValue> damage, List<EstimatedValue> heal) { }
}
