package net.spell_engine.internals.target;

import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.spell_engine.api.spell.Spell;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Optional;

/// Classifies what a spell means to do to whoever it reaches: whether it focuses a single target or
/// an area, and whether each of its impacts is helpful or harmful. Targeting reads this to decide
/// who may legally be hit (see {@link EntityRelations}).
public class SpellIntents {

    public static SpellTarget.FocusMode focusMode(Spell spell) {
        switch (spell.target.type) {
            case AREA, BEAM -> {
                return SpellTarget.FocusMode.AREA;
            }
            case NONE, CASTER, AIM, FROM_TRIGGER -> {
                return SpellTarget.FocusMode.DIRECT;
            }
        }
        assert true;
        return null;
    }

    public static Optional<SpellTarget.Intent> deliveryIntent(Spell spell) {
        switch (spell.deliver.type) {
            case STASH_EFFECT -> {
                var intent = intentForStatusEffect(spell.deliver.stash_effect.id);
                return Optional.of(intent);
            }
            default -> {
                return Optional.empty();
            }
        }
    }

    public static EnumSet<SpellTarget.Intent> impactIntents(Spell spell) {
        var intents = new HashSet<SpellTarget.Intent>();
        for (var impact: spell.impacts) {
            intents.add(impactIntent(impact.action));
            //return intent(impact.action);
        }
        return EnumSet.copyOf(intents);
    }

    public static SpellTarget.Intent impactIntent(Spell.Impact.Action action) {
        switch (action.type) {
            case DAMAGE, FIRE, AGGRO, DISRUPT -> {
                return SpellTarget.Intent.HARMFUL;
            }
            case HEAL, IMMUNITY -> {
                return SpellTarget.Intent.HELPFUL;
            }
            case STATUS_EFFECT -> {
                if (action.status_effect.remove != null) {
                    return action.status_effect.remove.select_beneficial ? SpellTarget.Intent.HARMFUL : SpellTarget.Intent.HELPFUL;
                }
                return intentForStatusEffect(action.status_effect.effect_id);
            }
            case SPAWN -> {
                var intent = SpellTarget.Intent.HELPFUL;
                if (!action.spawns.isEmpty()) {
                    intent = action.spawns.getFirst().intent;
                }
                return intent;
            }
            case SUMMON -> {
                return SpellTarget.Intent.HELPFUL;
            }
            case TELEPORT -> {
                return action.teleport.intent;
            }
            case COOLDOWN -> {
                var cooldown = action.cooldown;
                if (cooldown != null) {
                    var duration_add = 0F;
                    var duration_multiplier = 1F;
                    if (cooldown.actives != null) {
                        duration_add += cooldown.actives.duration_add;
                        duration_multiplier += cooldown.actives.duration_multiplier - 1;
                    }
                    if (cooldown.passives != null) {
                        duration_add += cooldown.passives.duration_add;
                        duration_multiplier += cooldown.passives.duration_multiplier - 1;
                    }
                    var addHelpful = duration_add <= 0;
                    var multiplierHelpful = duration_multiplier <= 1;
                    return addHelpful && multiplierHelpful ? SpellTarget.Intent.HELPFUL : SpellTarget.Intent.HARMFUL;
                }
                return SpellTarget.Intent.HELPFUL;
            }
            case VELOCITY -> {
                return action.velocity.intent;
            }
            case CUSTOM -> {
                return action.custom.intent;
            }
        }
        assert true;
        return null;
    }

    private static SpellTarget.Intent intentForStatusEffect(String idString) {
        var id = Identifier.of(idString);
        var effect = Registries.STATUS_EFFECT.get(id);
        return effect.isBeneficial() ? SpellTarget.Intent.HELPFUL : SpellTarget.Intent.HARMFUL;
    }
}
