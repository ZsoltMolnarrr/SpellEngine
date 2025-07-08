package net.spell_engine.internals;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.spell_engine.api.event.CombatEvents;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.container.SpellContainerHelper;
import net.spell_engine.api.spell.event.SpellEvents;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.compat.MeleeCompat;
import net.spell_engine.internals.arrow.ArrowExtension;
import net.spell_engine.internals.casting.SpellBatcher;
import net.spell_engine.internals.casting.SpellCast;
import net.spell_engine.internals.casting.SpellCasterEntity;
import net.spell_engine.internals.container.SpellContainerSource;
import net.spell_engine.internals.delivery.SpellStashHelper;
import net.spell_engine.internals.target.SpellTarget;
import net.spell_engine.mixin.entity.LivingEntityAccessor;
import net.spell_engine.utils.ObjectHelper;
import net.spell_engine.utils.PatternMatching;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Random;

public class SpellTriggers {
    public static class Event {
        /// Type of the trigger
        public final Spell.Trigger.Type type;
        public Spell.Trigger.Stage stage = Spell.Trigger.Stage.POST;
        /// Player that triggers the event
        public final PlayerEntity player;
        /// Entity to be used as the source of the area of effect
        @Nullable private final Entity aoeSource;
        /// Target of the player, or the entity that deals damage against the player
        @Nullable private final Entity target;
        /// Arrow that was fired
        public ArrowExtension arrow;

        @Nullable public RegistryEntry<Spell> spell;
        @Nullable public Spell.Impact impact;
        boolean criticalImpact = false;

        @Nullable public DamageSource damageSource;
        public float damageAmount = 0;

        @Nullable public MeleeCompat.Attack melee;

        public Event(Spell.Trigger.Type type, PlayerEntity player, @Nullable Entity aoeSource, @Nullable Entity target) {
            this.type = type;
            this.player = player;
            this.aoeSource = aoeSource;
            this.target = target;
        }

        private Entity entityFromSelector(Spell.Trigger.TargetSelector selector) {
            switch (selector) {
                case CASTER -> {
                    return player;
                }
                case AOE_SOURCE -> {
                    return aoeSource;
                }
                case TARGET -> {
                    return target;
                }
            }
            assert true;
            return null;
        }

        public Entity target(Spell.Trigger trigger) {
            if (trigger.target_override != null) {
                return entityFromSelector(trigger.target_override);
            }
            return ObjectHelper.coalesce(target, aoeSource, player);
        }

        public Entity aoeSource(Spell.Trigger trigger) {
            if (trigger.aoe_source_override != null) {
                return entityFromSelector(trigger.aoe_source_override);
            }
            return ObjectHelper.coalesce(aoeSource, target, player);
        }
    }

    public static void init() {
        CombatEvents.PLAYER_MELEE_ATTACK.register(args -> {
            onMeleeImpact(args.player(), args.target());
        });
        CombatEvents.PLAYER_DAMAGE_TAKEN.register(args -> {
            onDamageTaken(args.player(), args.source(), args.amount());
        });
        CombatEvents.PLAYER_SHIELD_BLOCK.register(args -> {
            onShieldBlock(args.player(), args.source(), args.amount());
        });
        SpellEvents.SPELL_CAST.register(args -> {
            onSpellCast(args.caster(), args.spell(), args.targets());
        });
    }

    public static void onArrowShot(ArrowExtension arrow, PlayerEntity player) {
        var event = new Event(Spell.Trigger.Type.ARROW_SHOT, player, player, null);
        event.arrow = arrow;
        fireTriggers(event);
    }

    public static void onArrowImpact(ArrowExtension arrow, PlayerEntity player, Entity target) {
        var event = new Event(Spell.Trigger.Type.ARROW_IMPACT, player, target, target);
        event.arrow = arrow;
        fireTriggers(event);
    }

    public static void onMeleeImpact(PlayerEntity player, Entity target) {
        var event = new Event(Spell.Trigger.Type.MELEE_IMPACT, player, target, target);
        if (target instanceof LivingEntity livingTarget) {
            event.damageSource = ((LivingEntityAccessor)livingTarget).getLastDamageSource();
            event.damageAmount = ((LivingEntityAccessor)livingTarget).getLastDamageTaken();
        }
        event.melee = MeleeCompat.attackProperties.apply(player);
        fireTriggers(event);
    }

    public static void onSpellImpactAny(PlayerEntity player, Entity target, Entity aoeSource, RegistryEntry<Spell> spell) {
        var event = new Event(Spell.Trigger.Type.SPELL_IMPACT_ANY, player, aoeSource, target);
        event.spell = spell;
        fireTriggers(event);
    }

    public static void onSpellImpactSpecific(PlayerEntity player, Entity target, RegistryEntry<Spell> spell, Spell.Impact impact, boolean critical, Spell.Trigger.Stage stage) {
        var event = new Event(Spell.Trigger.Type.SPELL_IMPACT_SPECIFIC, player, target, target);
        event.spell = spell;
        event.impact = impact;
        event.criticalImpact = critical;
        event.stage = stage;
        fireTriggers(event);
    }

    public static void onSpellCast(PlayerEntity player, RegistryEntry<Spell> spell, List<Entity> targets) {
        var firstTarget = targets.isEmpty() ? null : targets.getFirst();
        var target = ObjectHelper.coalesce(firstTarget, player);
        var event = new Event(Spell.Trigger.Type.SPELL_CAST, player, player, target);
        event.spell = spell;
        fireTriggers(event);
    }

    public static void onDamageTaken(PlayerEntity player, DamageSource source, float amount) {
        Entity sourceEntity = source.getAttacker();
        if (sourceEntity == null) {
            return; // No event without attacker (environmental damage)
        }
        Entity aoeSourceEntity = ObjectHelper.coalesce(sourceEntity, player);
        var event = new Event(Spell.Trigger.Type.DAMAGE_TAKEN, player, aoeSourceEntity, sourceEntity);
        event.damageSource = source;
        event.damageAmount = amount;
        fireTriggers(event);
    }

    public static void onShieldBlock(PlayerEntity player, DamageSource source, float amount) {
        Entity sourceEntity = source.getAttacker();
        if (sourceEntity == null) {
            return; // No event without attacker (environmental damage)
        }
        var event = new Event(Spell.Trigger.Type.SHIELD_BLOCK, player, player, sourceEntity);
        event.damageSource = source;
        event.damageAmount = amount;
        fireTriggers(event);
    }

    public static void onRoll(PlayerEntity player) {
        var event = new Event(Spell.Trigger.Type.ROLL, player, player, null);
        fireTriggers(event);
    }

    private static void fireTriggers(Event event) {
        if (event.player.getWorld().isClient()) { return; }
        // Iterate stash effects
        SpellStashHelper.useStashes(event);
        // Iterate passive spells
        var player = event.player;
        var caster = (SpellCasterEntity)player;
        for(var spellEntry: SpellContainerSource.passiveSpellsOf(event.player)) {
            var spell = spellEntry.value();
            var spellId = spellEntry.getKey().get().getValue();
            if (spell.passive != null && !caster.getCooldownManager().isCoolingDown(spellId)) {
                for (var trigger : spell.passive.triggers) {
                    if (evaluateTrigger(spellEntry, trigger, event)) {
                        SpellTarget.SearchResult targetResult;
                        if (spell.target.type == Spell.Target.Type.FROM_TRIGGER) {
                            List<Entity> targets = List.of(event.target(trigger));
                            targetResult = SpellTarget.SearchResult.of(targets);
                        } else {
                            targetResult = SpellTarget.findTargets(player, spellEntry, SpellTarget.SearchResult.empty(), true);
                        }
                        SpellHelper.performSpell(player.getWorld(), player, spellEntry, targetResult, SpellCast.Action.TRIGGER, 1);
                        break;
                    }
                }
            }
        }
    }

    private static final Random random = new Random();
    public static boolean evaluateTrigger(RegistryEntry<Spell> spellEntry, Spell.Trigger trigger, Event event) {
        if (trigger.type != event.type) {
            return false;
        }
        if (trigger.stage != event.stage) {
            return false;
        }
        var spellId = spellEntry.getKey().get().getValue();
        int triggerCount = 0;
        if (trigger.cap_per_tick > 0) {
            triggerCount = ((SpellBatcher)event.player).getBatchTriggerCount(spellId);
            if (triggerCount >= trigger.cap_per_tick) {
                return false;
            }
            triggerCount += 1;
        }
        if (trigger.chance < 1) {
            float randomValue;
            if (trigger.chance_batching) {
                var batchedChances = ((SpellBatcher)event.player).getBatchTriggerChance(spellId);
                if (batchedChances == null) {
                    randomValue = random.nextFloat();
                    ((SpellBatcher)event.player).batchTriggerChance(spellId, randomValue);
                } else {
                    randomValue = batchedChances;
                }
            } else {
                randomValue = random.nextFloat();
            }

            if (randomValue > trigger.chance) {
                return false;
            }
        }
        if (trigger.caster_conditions != null) {
            for (var condition : trigger.caster_conditions) {
                if (!SpellTarget.evaluate(event.player, event.target, condition)) {
                    return false;
                }
            }
        }
        if (event.target != null && trigger.target_conditions != null) {
            for (var condition : trigger.target_conditions) {
                if (!SpellTarget.evaluate(event.target, event.player, condition)) {
                    return false;
                }
            }
        }
        if (trigger.equipment_condition != null) {
            /**
             * The primary use case of this:
             * Avoid triggering main-hand spells from off-hand strikes (and vice versa)
             * Needs `equipment_condition` to be set to `MAINHAND`
             */
            var container = SpellContainerHelper.containerFromItemStack(event.player.getEquippedStack(trigger.equipment_condition));
            if (container == null || !container.contains(spellId)) {
                return false;
            }
        }

        boolean result;
        switch (trigger.type) {
            case SPELL_CAST, SPELL_IMPACT_ANY -> {
                result = evaluate(event.spell, trigger.spell);
            }
            case SPELL_IMPACT_SPECIFIC -> {
                result = evaluate(event.spell, trigger.spell) && evaluate(event.impact, event.criticalImpact, trigger.impact);
            }
            case MELEE_IMPACT -> {
                result = evaluate(event.melee, trigger.melee);
            }
            default -> {
                result = true;
            }
        }
        if (result) {
            if (triggerCount > 0) {
                ((SpellBatcher)event.player).batchTriggerCount(spellId, triggerCount);
            }
        }
        return result;
    }

    private static boolean evaluate(@Nullable RegistryEntry<Spell> spellEntry, @Nullable Spell.Trigger.SpellCondition condition) {
        if (condition == null) {
            return true;
        }
        if (spellEntry == null) {
            return false;
        }
        var spell = spellEntry.value();
        if (condition.school != null
                && !PatternMatching.regexMatches(spell.school.id.toString(), condition.school.toLowerCase()) ) {
            return false;
        }
        if (condition.id != null
                && !PatternMatching.matches(spellEntry, SpellRegistry.KEY, condition.id)) {
            return false;
        }
        if (condition.archetype != null
                && condition.archetype != spell.school.archetype) {
            return false;
        }
        if (condition.type != null
                && condition.type != spell.type) {
            return false;
        }
        if (condition.cooldown_min > 0) {
            if (spell.cost.cooldown == null || spell.cost.cooldown.duration < condition.cooldown_min) {
                return false;
            }
        }
        return true;
    }

    private static boolean evaluate(@Nullable Spell.Impact impact, boolean eventImpactIsCritical, @Nullable Spell.Trigger.ImpactCondition condition) {
        if (condition == null) {
            return true;
        }
        if (impact == null) {
            return false;
        }
        if (condition.impact_type != null
                && !PatternMatching.regexMatches(condition.impact_type.toLowerCase(), impact.action.type.toString().toLowerCase())) {
            return false;
        }
        if (condition.critical != null
                && condition.critical != eventImpactIsCritical) {
            return false;
        }
        return true;
    }

    private static boolean evaluate(@Nullable MeleeCompat.Attack melee, @Nullable Spell.Trigger.MeleeCondition condition) {
        if (condition == null) {
            return true;
        }
        if (melee == null) {
            return false;
        }
        if (condition.is_combo != null && melee.isCombo() != condition.is_combo) {
            return false;
        }
        if (condition.is_offhand != null && melee.isOffhand() != condition.is_offhand) {
            return false;
        }
        return true;
    }
}