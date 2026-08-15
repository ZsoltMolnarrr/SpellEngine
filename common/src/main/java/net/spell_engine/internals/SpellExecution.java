package net.spell_engine.internals;

import com.google.common.base.Suppliers;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.spell_engine.Platform;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.event.SpellEvents;
import net.spell_engine.fx.ReleaseFx;
import net.spell_engine.internals.casting.SpellCast;
import net.spell_engine.internals.casting.SpellCasterEntity;
import net.spell_engine.internals.casting.SpellCastSyncHelper;
import net.spell_engine.internals.casting.SpellCasting;
import net.spell_engine.internals.container.SpellContainerSource;
import net.spell_engine.internals.cost.Ammo;
import net.spell_engine.internals.cost.SpellCooldowns;
import net.spell_engine.internals.cost.SpellCost;
import net.spell_engine.internals.delivery.SpellDelivery;
import net.spell_engine.internals.impact.SpellEstimation;
import net.spell_engine.internals.impact.SpellImpacts;
import net.spell_engine.internals.target.SpellIntents;
import net.spell_engine.internals.target.SpellTarget;
import net.spell_engine.utils.AnimationHelper;
import net.spell_power.api.SpellPower;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/// The heart of a spell execution: the entry points that fire a spell, and the vocabulary the
/// stages hand each other along the way. The flow, per the pipeline order in spell data:
///
/// ```
/// [CAST]    performSpell (player) / targetAndPerformSpell (entity) — the cast bar itself is
///           managed by casting.SpellCasting; this is what runs when it fires or ticks
/// [TARGET]  client raycast (player, arrives on the SearchResult) / SpellTarget.findTargets (entity)
/// [DELIVER] SpellDelivery.resolveAndDeliver → deliver
///             DIRECT / AREA ──────► SpellImpacts.performImpacts    (same tick)
///             PROJECTILE / METEOR ► SpellProjectile ► SpellImpacts (on hit / landing)
///             CLOUD ──────────────► SpellCloud ► SpellImpacts      (while ticking)
///             MELEE ──────────────► client swing ► SpellImpacts    (on contact)
/// [IMPACT]  SpellImpacts — actions, area impact fan-out, summons
/// [COST]    the completion callback, invoked with the delivery's outcome:
///           release FX (fx.ReleaseFx) + cost.SpellCost.consume (cooldown, exhaust, durability, ammo)
/// ```
///
/// Side doors that enter mid-pipeline by design: passive triggers re-enter through `performSpell`
/// with the TRIGGER action; stashed spells (SpellStashHelper) and arrow-carried spells
/// (PersistentProjectileEntityMixin) call into SpellImpacts directly.
public class SpellExecution {

    // MARK: Entry points

    public static void performSpell(World world, PlayerEntity player, RegistryEntry<Spell> spellEntry, SpellTarget.SearchResult targetResult, SpellCast.Action action, float progress) {
        if (player.isSpectator()) { return; }
        var spell = spellEntry.value();
        var spellId = spellEntry.getKey().get().getValue();

        var heldItemStack = player.getMainHandStack();
        var spellSource = SpellContainerSource.getFirstSourceOfSpell(spellId, player);
        if (spellSource == null) {
            return;
        }
        var attempt = SpellCasting.attempt(player, heldItemStack, spellId);
        if (!attempt.isSuccess()) {
            return;
        }
        var caster = (SpellCasterEntity)player;
        var targets = targetResult.entities();
        var castingSpeed = caster.getCurrentCastingSpeed();
        // Normalized progress in 0 to 1
        progress = Math.max(Math.min(progress, 1F), 0F);
        var channelMultiplier = 1F;
        var channelTickIndex = 0;
        int incrementChannelTicks = 0;
        boolean shouldPerformImpact = true;
        Spell.Modifier chargeModifier = null;
        var curvedRatio = 1F;
        Supplier<Collection<ServerPlayerEntity>> trackingPlayers = Suppliers.memoize(() -> { // Suppliers.memoize = Lazy
            return Platform.tracking(player);
        });
        switch (action) {
            case CHANNEL -> {
                channelTickIndex = caster.getChannelTickIndex();
                incrementChannelTicks = 1;
                channelMultiplier = SpellParameters.channelValueMultiplier(spell);
                // Compensating with extra damage, for spell with less than intended ticks
                // (due to tick interval shorter than 1 tick.)
                if (caster.getSpellCastProcess() != null) {
                    var channelInterval = caster.getSpellCastProcess().channelInterval(player);
                    if (channelInterval < 1) {
                        channelMultiplier *= (1F / channelInterval);
                    }
                }
            }
            case RELEASE -> {
                var cast = spell.active.cast;
                if (SpellParameters.isChanneled(spell)) {
                    shouldPerformImpact = false;
                    channelMultiplier = 1;
                } else if (cast.type == Spell.Active.Cast.Type.CHARGE && cast.charge != null) {
                    // `progress` is the charge ratio (already clamped to 0..1). Below the minimum it
                    // fizzles via channelMultiplier = 0 (reusing the delivery gate below); otherwise
                    // the curved ratio scales the charge bonus modifier and is carried on the
                    // ImpactContext, which applies `output_scaling` to it on read.
                    if (progress >= cast.charge.min_release_ratio) {
                        curvedRatio = cast.charge.curve.apply(progress);
                        chargeModifier = SpellModifiers.scaledBy(cast.charge.bonus, curvedRatio);
                        channelMultiplier = 1;
                    } else {
                        channelMultiplier = 0;
                    }
                } else {
                    channelMultiplier = (progress >= 1) ? 1 : 0;
                }
                SpellCastSyncHelper.clearCasting(player);
            }
            case TRIGGER -> {
                // Nothing to do, defaults are okay
            }
        }
        var ammoResult = Ammo.ammoForSpell(player, spell, heldItemStack);

        if (channelMultiplier > 0 && ammoResult.satisfied()) {
            var targeting = spell.target;
            boolean finished = action == SpellCast.Action.RELEASE
                    || (action == SpellCast.Action.TRIGGER && spell.type == Spell.Type.PASSIVE); // For stashed spells release has been done already
            boolean shouldSendReleaseFx = switch (action) {
                case CHANNEL -> spell.active.cast.channelReleaseFx();
                case RELEASE -> !(SpellParameters.isChanneled(spell) && spell.active.cast.channelReleaseFx());
                case TRIGGER -> spell.type == Spell.Type.PASSIVE;
            };
            boolean success = true;
            if (targeting.cap > 0) {
                targets = targets.stream()
                        .sorted(Comparator.comparingDouble(target -> target.squaredDistanceTo(player.getPos())))
                        .limit(targeting.cap)
                        .toList();
            }

            Consumer<DeliveryCompletion> completion = null;
            if (shouldSendReleaseFx || finished) {
                float finalProgress = progress;
                List<Entity> finalTargets = targets;
                completion = (completionArgs) -> {
                    if (!completionArgs.success()) return;
                    if (shouldSendReleaseFx) {
                        ReleaseFx.send(world, player, spellEntry, finalProgress);
                        AnimationHelper.sendAnimation(player, trackingPlayers.get(), SpellCast.Animation.RELEASE, spell.release.animation, castingSpeed);
                    }
                    if (finished) {
                        SpellCost.consume(player, finalProgress, spellSource, spellId, spellEntry, heldItemStack, ammoResult, false);
                        var args = new SpellEvents.SpellCastEvent.Args(player, spellEntry, finalTargets, action, finalProgress);
                        SpellEvents.SPELL_CAST.invoke((listener) -> listener.onSpellCast(args));
                    }
                };
            }

            if (shouldPerformImpact) {
                SpellCooldowns.imposeAttemptCooldown(player, spellEntry);
                // Channel tick or charge release
                success = false;
                var context = new ImpactContext(channelMultiplier,
                        1F,
                        null,
                        SpellPower.getSpellPower(spell.school, player),
                        SpellIntents.focusMode(spell),
                        channelTickIndex)
                        .chargeModifier(chargeModifier)
                        .charge(curvedRatio);
                success = SpellDelivery.resolveAndDeliver(world, player, spellEntry, targetResult, context, completion);
                // Only a channel tick of the spell actually being cast may touch the counter.
                // Without the guard every other action lands here with both locals left at zero
                // (TRIGGER takes the defaults, RELEASE of a non-channeled spell never sets them),
                // so a passive proc or an unrelated cast would silently reset the index mid-channel
                // and repeat the swing the previous tick already performed. The cast process match
                // keeps a channel request naming another spell from shifting this one's index —
                // only one spell can be cast at a time, so anything else is out of band.
                var castProcess = caster.getSpellCastProcess();
                if (incrementChannelTicks > 0
                        && castProcess != null
                        && castProcess.id().equals(spellId)) {
                    caster.setChannelTickIndex(channelTickIndex + incrementChannelTicks);
                }
            } else {
                if (finished && completion != null) {
                    // Channel release
                    completion.accept(new DeliveryCompletion(true));
                }
            }
        }
    }

    /// Server-side entry point for non-player entities (e.g. summoned companions) to cast a spell.
    /// Performs targeting based on the entity's current rotation/position, then runs the full
    /// delivery pipeline. No player-side costs (ammo, exhaust, cooldown, animations) are applied;
    /// callers are responsible for managing their own cooldown.
    ///
    /// Must be called on the server; silently no-ops on the client.
    public static void targetAndPerformSpell(World world, LivingEntity caster, RegistryEntry<Spell> spellEntry) {
        if (world.isClient()) return;
        var spell = spellEntry.value();
        if (spell.active == null) return;

        var context = new ImpactContext()
                .power(SpellPower.getSpellPower(spell.school, caster))
                .target(SpellIntents.focusMode(spell));

        if (!caster.isAttackable() && caster instanceof Tameable) {
            var owner = ((Tameable) caster).getOwner();
            if (owner != null) {
                context = context.effectiveCaster(owner);
            }
        }

        var targetResult = SpellTarget.findTargets(caster, spellEntry, SpellTarget.SearchResult.empty(), true);

        // Apply target cap, sorted by distance to caster
        var targets = targetResult.entities();
        if (spell.target.cap > 0) {
            targets = targets.stream()
                    .sorted(Comparator.comparingDouble(t -> t.squaredDistanceTo(caster.getPos())))
                    .limit(spell.target.cap)
                    .toList();
            targetResult = new SpellTarget.SearchResult(targets, targetResult.location());
        }

        SpellDelivery.resolveAndDeliver(world, caster, spellEntry, targetResult, context,
                (completionArgs) -> {
                    if (completionArgs.success()) {
                        // Summons never cast CHARGE spells, so the pitch shift never applies; pass full.
                        ReleaseFx.send(world, caster, spellEntry, 1F);
                    }
                });
    }

    // MARK: Impact context

    /// The payload carried from the cast stage all the way down to individual impacts: the output
    /// multipliers accumulated so far, the resolved spell power, and the anchor position.
    ///
    /// `charge` is `curve(hold ratio)` of a CHARGE release — curved, but deliberately NOT weighted by
    /// `output_scaling`. That weighting is applied on read, by `total`, so the unweighted ratio stays
    /// the single source of truth: `output_scaling = 0` would otherwise flatten it to a constant `1`
    /// and erase the hold time, which deliveries that resume on a later call stack (MELEE) still
    /// need. **It is therefore not an output multiplier — scale damage with `total(spellEntry)`, not
    /// with this.** `chargeModifier` is the charge bonus already scaled by the same ratio, memoized
    /// here rather than rebuilt per impact. Both are inert (`1` / null) for other casts.
    public record ImpactContext(float channel, float distance, @Nullable Vec3d position,
                                SpellPower.Result power, SpellTarget.FocusMode focusMode,
                                int channelTickIndex, @Nullable int effectiveCasterId,
                                @Nullable Spell.Modifier chargeModifier, float charge) {
        public ImpactContext() {
            this(1, 1, null, null, SpellTarget.FocusMode.DIRECT, 0, 0, null, 1);
        }

        public ImpactContext(float channel, float distance, @Nullable Vec3d position,
                             SpellPower.Result power, SpellTarget.FocusMode focusMode,
                             int channelTickIndex) {
            this(channel, distance, position, power, focusMode, channelTickIndex, 0, null, 1);
        }

        public ImpactContext channeled(float multiplier) {
            return new ImpactContext(multiplier, distance, position, power, focusMode, channelTickIndex, effectiveCasterId, chargeModifier, charge);
        }

        public ImpactContext distance(float multiplier) {
            return new ImpactContext(channel, multiplier, position, power, focusMode, channelTickIndex, effectiveCasterId, chargeModifier, charge);
        }

        public ImpactContext position(Vec3d position) {
            return new ImpactContext(channel, distance, position, power, focusMode, channelTickIndex, effectiveCasterId, chargeModifier, charge);
        }

        public ImpactContext power(SpellPower.Result spellPower) {
            return new ImpactContext(channel, distance, position, spellPower, focusMode, channelTickIndex, effectiveCasterId, chargeModifier, charge);
        }

        public ImpactContext target(SpellTarget.FocusMode focusMode) {
            return new ImpactContext(channel, distance, position, power, focusMode, channelTickIndex, effectiveCasterId, chargeModifier, charge);
        }

        public ImpactContext effectiveCaster(@Nullable LivingEntity effectiveCaster) {
            var id = effectiveCaster != null ? effectiveCaster.getId() : 0;
            return new ImpactContext(channel, distance, position, power, focusMode, channelTickIndex, id, chargeModifier, charge);
        }

        public ImpactContext chargeModifier(@Nullable Spell.Modifier chargeModifier) {
            return new ImpactContext(channel, distance, position, power, focusMode, channelTickIndex, effectiveCasterId, chargeModifier, charge);
        }

        public ImpactContext charge(float charge) {
            return new ImpactContext(channel, distance, position, power, focusMode, channelTickIndex, effectiveCasterId, chargeModifier, charge);
        }

        public @Nullable LivingEntity effectiveCaster(World world) {
            return effectiveCasterId != 0 ? (LivingEntity) world.getEntityById(effectiveCasterId) : null;
        }

        public boolean hasOffset() {
            return position != null;
        }

        public Vec3d knockbackDirection(Vec3d targetPosition) {
            return targetPosition.subtract(position).normalize();
        }

        public boolean isChanneled() {
            return channel != 1;
        }

        public float total(@Nullable RegistryEntry<Spell> spellEntry) {
            return channel * distance * SpellParameters.chargeOutputMultiplier(spellEntry, charge);
        }
    }

    // MARK: Delivery

    /// A resolved delivery recipient, paired with the context specific to it (area spells, for
    /// example, hand each target its own distance-based multiplier).
    public record DeliveryTarget(Entity entity, ImpactContext context) {}

    /// Handed to the delivery completion callback once a delivery has run. `success` gates the
    /// consumption of a spell's cost — a delivery that never landed must not put it on cooldown.
    public record DeliveryCompletion(boolean success) {}

    // MARK: Impact conditions

    /// Outcome of evaluating an impact's target conditions: whether the impact may proceed at all,
    /// plus the power modifiers contributed by the conditions that matched.
    public record ConditionResult(boolean allowed, List<Spell.Impact.Modifier> modifiers) {
        public static final ConditionResult ALLOWED = new ConditionResult(true, List.of());
        public static final ConditionResult DENIED = new ConditionResult(false, List.of());
    }

    // MARK: Power

    /// Resolution of the spell power an impact acts with. Shared by impact execution
    /// ({@link SpellImpacts}) and output estimation ({@link SpellEstimation}) so that the numbers a
    /// tooltip promises and the numbers a hit delivers cannot drift apart.
    public static class Power {

        /// Resolves an impact's hybrid power: merges each `power_blend` component's school power into
        /// `base` as a per-aspect weighted average. The base result participates with weight 1 in every
        /// aspect; a component only contributes to (and only adds its weight to the denominator of) the
        /// aspects it lists. Returns `base` untouched when there is nothing to blend.
        public static SpellPower.Result blended(SpellPower.Result base, @Nullable List<Spell.Impact.PowerBlend> blend, LivingEntity caster) {
            if (blend == null || blend.isEmpty()) {
                return base;
            }
            double power = base.baseValue();
            double critChance = base.criticalChance();
            double critDamage = base.criticalDamage();
            double powerWeight = 1, critChanceWeight = 1, critDamageWeight = 1;
            for (var component: blend) {
                if (component.school == null || component.weight <= 0) {
                    continue;
                }
                if (!component.power && !component.critical_chance && !component.critical_damage) {
                    continue;
                }
                var other = SpellPower.getSpellPower(component.school, caster);
                if (component.power) {
                    power += other.baseValue() * component.weight;
                    powerWeight += component.weight;
                }
                if (component.critical_chance) {
                    critChance += other.criticalChance() * component.weight;
                    critChanceWeight += component.weight;
                }
                if (component.critical_damage) {
                    critDamage += other.criticalDamage() * component.weight;
                    critDamageWeight += component.weight;
                }
            }
            return new SpellPower.Result(base.school(),
                    power / powerWeight,
                    critChance / critChanceWeight,
                    critDamage / critDamageWeight);
        }

        /// Resolves an impact's base power: base school power (impact `school`, or the spell's
        /// school), then the `attribute` power override, then `power_blend` merging.
        /// `cached` is reused when it already holds the resolved school's power.
        /// `attributeTarget` is the entity read by `attribute_from_target` (execution passes the
        /// struck target; estimation passes null, falling back to the caster).
        public static SpellPower.Result resolve(Spell spell, Spell.Impact impact, LivingEntity caster,
                                                @Nullable SpellPower.Result cached, @Nullable Entity attributeTarget) {
            var school = impact.school != null ? impact.school : spell.school;
            var power = cached;
            if (power == null || power.school() != school) {
                power = SpellPower.getSpellPower(school, caster);
            }
            if (impact.attribute != null && !impact.attribute.isEmpty()) {
                var attributeEntry = Registries.ATTRIBUTE.getEntry(Identifier.of(impact.attribute));
                if (attributeEntry.isPresent()) {
                    var attribute = attributeEntry.get();
                    double value;
                    if (impact.attribute_from_target && attributeTarget instanceof LivingEntity livingEntity) {
                        value = livingEntity.getAttributes().hasAttribute(attribute)
                                ? livingEntity.getAttributeValue(attribute)
                                : 0;
                    } else {
                        value = caster.getAttributeValue(attribute);
                    }
                    power = new SpellPower.Result(power.school(), value, power.criticalChance(), power.criticalDamage());
                }
            }
            return blended(power, impact.power_blend, caster);
        }

        /// Clamps a power result to the impact action's `min_power`/`max_power` bounds.
        public static SpellPower.Result clamped(SpellPower.Result power, Spell.Impact.Action action) {
            if (power.baseValue() < action.min_power || power.baseValue() > action.max_power) {
                var clampedValue = MathHelper.clamp(power.baseValue(), action.min_power, action.max_power);
                return new SpellPower.Result(power.school(), clampedValue, power.criticalChance(), power.criticalDamage());
            }
            return power;
        }
    }
}
