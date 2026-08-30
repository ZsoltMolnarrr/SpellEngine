package net.spell_engine.internals;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.spell_engine.api.event.CombatEvents;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.container.SpellContainerHelper;
import net.spell_engine.api.spell.event.SpellEvents;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.compat.MeleeCompat;
import net.spell_engine.compat.CriticalStrikeCompat;
import net.spell_engine.internals.delivery.arrow.ArrowExtension;
import net.spell_engine.internals.casting.SpellBatcher;
import net.spell_engine.internals.casting.SpellCast;
import net.spell_engine.internals.casting.SpellCaster;
import net.spell_engine.internals.container.SpellContainerSource;
import net.spell_engine.internals.delivery.SpellStashHelper;
import net.spell_engine.internals.target.SpellTarget;
import net.spell_engine.mixin.entity.LivingEntityAccessor;
import net.spell_engine.utils.ObjectHelper;
import net.spell_engine.utils.PatternMatching;
import net.spell_engine.utils.WorldScheduler;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;

public class SpellTriggers {
    public static class Event {
        /// Type of the trigger
        public final Spell.Trigger.Type type;
        public Spell.Trigger.Stage stage = Spell.Trigger.Stage.POST;
        /// Player that triggers the event
        public final Player player;
        /// Entity to be used as the source of the area of effect
        @Nullable private final Entity aoeSource;
        /// Target of the player, or the entity that deals damage against the player
        @Nullable private final Entity target;
        /// Arrow that was fired
        public ArrowExtension arrow;
        /// Location of the trigger
        @Nullable private Vec3 location;

        @Nullable public Holder<Spell> spell;
        @Nullable public Spell.Impact impact;
        boolean criticalImpact = false;

        @Nullable public DamageSource damageSource;
        public float damageAmount = 0;
        public boolean damageFatal = false;

        @Nullable public MeleeCompat.Attack melee;

        @Nullable public Holder<MobEffect> statusEffect;

        public boolean arrowFiredBySpell = false;

        public Event(Spell.Trigger.Type type, Player player, @Nullable Entity aoeSource, @Nullable Entity target) {
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
        CombatEvents.PLAYER_DAMAGE_INCOMING.register(args -> {
            onDamageIncoming(args.player(), args.source(), args.amount());
        });
        CombatEvents.PLAYER_SHIELD_BLOCK.register(args -> {
            onShieldBlock(args.player(), args.source(), args.amount());
        });
        SpellEvents.SPELL_CAST.register(args -> {
            onSpellCast(args.caster(), args.spell(), args.targets());
        });
        CombatEvents.ENTITY_EVASION.register(args -> {
            onEvasion(args.entity(), args.damageAmount(), args.source());
        });
    }

    public static void onArrowShot(ArrowExtension arrow, Player player, boolean firedBySpell) {
        var event = new Event(Spell.Trigger.Type.ARROW_SHOT, player, player, null);
        event.stage = Spell.Trigger.Stage.POST;
        event.arrow = arrow;
        event.arrowFiredBySpell = firedBySpell;
        fireTriggers(event);
    }

    public static void onArrowImpact(ArrowExtension arrow, Player player, Entity target, DamageSource damageSource, float damageAmount) {
        var event = new Event(Spell.Trigger.Type.ARROW_IMPACT, player, target, target);
        event.arrow = arrow;
        event.damageSource = damageSource;
        event.damageAmount = damageAmount;
        event.criticalImpact = CriticalStrikeCompat.isCriticalStrike(damageSource);
        fireTriggers(event);
    }

    public static void onMeleeImpact(Player player, Entity target) {
        var event = new Event(Spell.Trigger.Type.MELEE_IMPACT, player, target, target);
        if (target instanceof LivingEntity livingTarget) {
            event.damageSource = livingTarget.getLastDamageSource();
            event.damageAmount = ((LivingEntityAccessor)livingTarget).spellEngine_getLastDamageTaken();
            event.criticalImpact = CriticalStrikeCompat.isCriticalStrike(event.damageSource);
        }
        event.melee = MeleeCompat.attackProperties.apply(player);

        var activeSpell = ((SpellCaster.Player)player).getActiveMeleeSkill();
        if (activeSpell != null) {
            event.spell = activeSpell;
        }
        fireTriggers(event);
    }

    public static void onSpellImpactAny(Player player, Entity target, Entity aoeSource, Holder<Spell> spell) {
        var event = new Event(Spell.Trigger.Type.SPELL_IMPACT_ANY, player, aoeSource, target);
        event.spell = spell;
        fireTriggers(event);
    }

    public static void onSpellImpactSpecific(Player player, Entity target, Holder<Spell> spell, Spell.Impact impact, boolean critical, Spell.Trigger.Stage stage) {
        var event = new Event(Spell.Trigger.Type.SPELL_IMPACT_SPECIFIC, player, target, target);
        event.spell = spell;
        event.impact = impact;
        event.criticalImpact = critical;
        event.stage = stage;
        fireTriggers(event);
    }

    public static void onSpellCast(Player player, Holder<Spell> spell, List<Entity> targets) {
        var firstTarget = targets.isEmpty() ? null : targets.getFirst();
        var target = ObjectHelper.coalesce(firstTarget, player);
        var event = new Event(Spell.Trigger.Type.SPELL_CAST, player, player, target);
        event.spell = spell;
        fireTriggers(event);
    }

    public static void onSpellAreaImpact(Player player, @Nullable Entity target, Vec3 location, Holder<Spell> spell) {
        var event = new Event(Spell.Trigger.Type.SPELL_AREA_IMPACT, player, target, target);
        event.location = location;
        event.spell = spell;
        fireTriggers(event);
    }

    public static void onEffectTick(Player player, Holder<MobEffect> effect) {
        var event = new Event(Spell.Trigger.Type.EFFECT_TICK, player, player, null);
        event.statusEffect = effect;
        fireTriggers(event);
    }

    public static void onDamageIncoming(Player player, DamageSource source, float amount) {
        Entity sourceEntity = source.getEntity();
        if (sourceEntity == null) {
            return; // No event without attacker (environmental damage)
        }
        var event = new Event(Spell.Trigger.Type.DAMAGE_TAKEN, player, player, sourceEntity);
        event.stage = Spell.Trigger.Stage.PRE;
        event.damageFatal = amount >= player.getHealth();
        event.damageSource = source;
        event.criticalImpact = CriticalStrikeCompat.isCriticalStrike(event.damageSource);
        event.damageAmount = amount;
        fireTriggers(event);
    }

    public static void onDamageTaken(Player player, DamageSource source, float amount) {
        Entity sourceEntity = source.getEntity();
        if (sourceEntity == null) {
            return; // No event without attacker (environmental damage)
        }
        Entity aoeSourceEntity = ObjectHelper.coalesce(sourceEntity, player);
        var event = new Event(Spell.Trigger.Type.DAMAGE_TAKEN, player, aoeSourceEntity, sourceEntity);
        event.damageSource = source;
        event.criticalImpact = CriticalStrikeCompat.isCriticalStrike(event.damageSource);
        event.damageAmount = amount;
        fireTriggers(event);
    }

    public static void onShieldBlock(Player player, DamageSource source, float amount) {
        Entity sourceEntity = source.getEntity();
        if (sourceEntity == null) {
            return; // No event without attacker (environmental damage)
        }
        var event = new Event(Spell.Trigger.Type.SHIELD_BLOCK, player, player, sourceEntity);
        event.damageSource = source;
        event.criticalImpact = CriticalStrikeCompat.isCriticalStrike(event.damageSource);
        event.damageAmount = amount;
        fireTriggers(event);
    }

    public static void onRoll(Player player) {
        var event = new Event(Spell.Trigger.Type.ROLL, player, player, null);
        fireTriggers(event);
    }

    public static void onEvasion(LivingEntity entity, float damageAmount, DamageSource source) {
        if (!(entity instanceof Player player)) {
            return;
        }
        Entity sourceEntity = source.getEntity();
        if (sourceEntity == null) {
            return; // No event without attacker (environmental damage)
        }
        var event = new Event(Spell.Trigger.Type.EVASION, player, player, sourceEntity);
        event.damageSource = source;
        event.criticalImpact = CriticalStrikeCompat.isCriticalStrike(event.damageSource);
        event.damageAmount = damageAmount;
        fireTriggers(event);
    }

    private static void fireTriggers(Event event) {
        if (event.player.level().isClientSide()) { return; }
        // Iterate stash effects
        SpellStashHelper.useStashes(event);
        // Iterate passive spells
        var player = event.player;
        var caster = (SpellCaster.Player)player;
        for(var spellEntry: SpellContainerSource.passiveSpellsOf(event.player)) {
            var spell = spellEntry.value();
            var spellId = spellEntry.unwrapKey().get().identifier();
            if (spell.passive != null && !caster.getCooldownManager().isCoolingDown(spellEntry)) {
                for (var trigger : spell.passive.triggers) {
                    if (evaluateTrigger(spellEntry, trigger, event)) {
                        SpellTarget.SearchResult targetResult;
                        if (spell.target.type == Spell.Target.Type.FROM_TRIGGER) {
                            if (event.target == null && event.location != null) {
                                targetResult = SpellTarget.SearchResult.of(event.location);
                            } else {
                                List<Entity> targets = List.of(event.target(trigger));
                                targetResult = SpellTarget.SearchResult.of(targets);
                            }
                        } else {
                            targetResult = SpellTarget.findTargets(player, spellEntry, SpellTarget.SearchResult.empty(), true);
                        }
                        if (trigger.fire_delay > 0) {
                            ((WorldScheduler)player.level()).schedule(trigger.fire_delay - 1, () -> {
                                SpellExecution.performSpell(player.level(), player, spellEntry, targetResult, SpellCast.Action.TRIGGER, 1);
                            });
                        } else {
                            SpellExecution.performSpell(player.level(), player, spellEntry, targetResult, SpellCast.Action.TRIGGER, 1);
                        }
                        break;
                    }
                }
            }
        }
    }

    private static final Random random = new Random();
    public static boolean evaluateTrigger(Holder<Spell> spellEntry, Spell.Trigger trigger, Event event) {
        if (trigger.type != event.type) {
            return false;
        }
        if (trigger.stage != event.stage) {
            return false;
        }
        var spellId = spellEntry.unwrapKey().get().identifier();
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
            var container = SpellContainerHelper.containerFromItemStack(event.player.getItemBySlot(trigger.equipment_condition));
            if (container == null || !container.contains(spellId)) {
                return false;
            }
        }
        if (trigger.weapon_condition != null) {
            var weapon = triggeringWeapon(event);
            if (weapon == null || weapon.isEmpty()
                    || !PatternMatching.matches(weapon.getItem().builtInRegistryHolder(), Registries.ITEM, trigger.weapon_condition)) {
                return false;
            }
        }

        boolean result;
        switch (trigger.type) {
            case SPELL_CAST, SPELL_IMPACT_ANY, SPELL_AREA_IMPACT -> {
                result = evaluateSpellCast(event.spell, trigger.spell)
                    && evaluateDamage(trigger.damage, event);
            }
            case SPELL_IMPACT_SPECIFIC -> {
                result = evaluateSpellCast(event.spell, trigger.spell)
                        && evaluateSpellImpact(event.impact, event, trigger.impact)
                        && evaluateDamage(trigger.damage, event);
            }
            case ARROW_IMPACT, EVASION -> {
                result = evaluateDamage(trigger.damage, event);
            }
            case MELEE_IMPACT -> {
                result = evaluateSpellCast(event.spell, trigger.spell)
                        && evaluateMelee(event, trigger.melee)
                        && evaluateDamage(trigger.damage, event);
            }
            case EFFECT_TICK -> {
                result = evaluateEffect(event, trigger.effect);
            }
            case DAMAGE_TAKEN -> {
                result = evaluateSpellImpact(null, event, trigger.impact)
                        && evaluateDamage(trigger.damage, event);
            }
            case ARROW_SHOT -> {
                result = evaluateArrowShot(event, trigger.arrow_shot);
            }
            case SHIELD_BLOCK, ROLL -> {
                result = true;
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

    /// The item that performed the triggering attack, matched against `Trigger.weapon_condition`.
    /// Arrows report the weapon they were actually fired from (null for spell-fired arrows),
    /// melee attacks the hand that struck, everything else falls back to the caster's main hand.
    @Nullable
    private static ItemStack triggeringWeapon(Event event) {
        switch (event.type) {
            case ARROW_SHOT, ARROW_IMPACT -> {
                if (event.arrow instanceof AbstractArrow projectile) {
                    return projectile.getWeaponItem();
                }
                return null;
            }
            case MELEE_IMPACT -> {
                var offhand = event.melee != null && event.melee.isOffhand();
                return offhand ? event.player.getOffhandItem() : event.player.getMainHandItem();
            }
            default -> {
                return event.player.getMainHandItem();
            }
        }
    }

    private static boolean evaluateSpellCast(@Nullable Holder<Spell> spellEntry, @Nullable Spell.Trigger.SpellCondition condition) {
        if (condition == null) {
            return true;
        }
        if (spellEntry == null) {
            return false;
        }
        var spell = spellEntry.value();
        if (condition.school != null
                && !PatternMatching.regexMatches(spell.school.id.toString(), condition.school.toLowerCase(Locale.ROOT)) ) {
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

    private static boolean evaluateSpellImpact(@Nullable Spell.Impact impact, Event event, @Nullable Spell.Trigger.ImpactCondition condition) {
        if (condition == null) {
            return true;
        }
        if (impact == null) {
            return false;
        }
        if (condition.impact_type != null
                && !PatternMatching.regexMatches(condition.impact_type.toLowerCase(Locale.ROOT), impact.action.type.toString().toLowerCase(Locale.ROOT))) {
            return false;
        }
        if (condition.critical != null
                && condition.critical != event.criticalImpact) {
            return false;
        }
        return true;
    }

    private static boolean evaluateDamage(@Nullable Spell.Trigger.DamageCondition condition, Event event) {
        if (condition == null) {
            return true;
        }
        if (condition.damage_type != null && event.damageSource != null
                && !PatternMatching.matches(event.damageSource.typeHolder(), Registries.DAMAGE_TYPE, condition.damage_type)) {
            return false;
        }
        if (condition.amount_min != null && event.damageAmount < condition.amount_min) {
            return false;
        }
        if (condition.amount_max != null && event.damageAmount > condition.amount_max) {
            return false;
        }
        if (condition.fatal != null && event.damageFatal != condition.fatal) {
            return false;
        }
        return true;
    }

    private static boolean evaluateMelee(Event event, @Nullable Spell.Trigger.MeleeCondition condition) {
        if (condition == null) {
            return true;
        }
        // Read off the event, not the melee attack properties, so it also works without Better Combat
        if (condition.critical != null && condition.critical != event.criticalImpact) {
            return false;
        }
        if (condition.is_combo != null || condition.is_offhand != null) {
            var melee = event.melee;
            if (melee == null) {
                return false;
            }
            if (condition.is_combo != null && melee.isCombo() != condition.is_combo) {
                return false;
            }
            if (condition.is_offhand != null && melee.isOffhand() != condition.is_offhand) {
                return false;
            }
        }
        return true;
    }

    private static boolean evaluateEffect(Event event, Spell.Trigger.EffectCondition condition) {
        if (condition == null) {
            return true;
        }
        if (event.statusEffect == null) {
            return false;
        }
        // PatternMatching.matches(event.statusEffect, Registries.STATUS_EFFECT.getKey(), condition.effect_id)
        // doesn't work due to the legacy type of Registries.STATUS_EFFECT
        var key = event.statusEffect.unwrapKey();
        if (condition.id != null && key.isPresent()
                && !Objects.equals(key.get().identifier().toString(), condition.id)) {
            return false;
        }
        return true;
    }

    private static boolean evaluateArrowShot(Event event, Spell.Trigger.ArrowShotCondition condition) {
        if (condition == null) {
            return true;
        }
        if (event.arrow == null) {
            return false;
        }
        if (condition.from_spell != null
                && event.arrowFiredBySpell != condition.from_spell) {
            return false;
        }
        return true;
    }
}