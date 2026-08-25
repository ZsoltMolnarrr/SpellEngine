package net.spell_engine.internals.casting;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Tameable;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.api.spell.summon.SummonBehaviour;
import net.spell_engine.entity.SummonedEntity;
import net.spell_engine.fx.ReleaseFx;
import net.spell_engine.internals.SpellExecution;
import net.spell_engine.internals.SpellParameters;
import net.spell_engine.internals.delivery.SpellDelivery;
import net.spell_engine.internals.target.SpellIntents;
import net.spell_engine.internals.target.SpellTarget;
import net.spell_engine.utils.SoundHelper;
import net.spell_power.api.SpellPower;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

/// Mob-side counterpart of {@link SpellCastInteractor}'s drivers: encapsulates the whole cast
/// lifecycle of a summon — option resolution (from its {@link SummonBehaviour} config), aim
/// strategies, cast timing (engagement-gated for timed casts; wall-clock schedule for channels,
/// declared via the entity's synced process for beam presentation), and the cost-free
/// server-side fire (formerly `SpellExecution.targetAndPerformSpell`). `SpellCastGoal` is a
/// thin vanilla-`Goal` adapter over this; a Brain-based mob or scripted caster can drive the
/// same lifecycle directly.
///
/// Deliberately NOT running on `SpellCastInteractor`'s timeline: a mob cast progresses only
/// while its aim is engaged (in range + line of sight), which is AI-native pacing the wall-clock
/// interactor cannot express. The vocabulary (`SpellParameters` timing, the delivery pipeline)
/// is shared; the pacing is the mob's own.
public class MobCastController {
    private final SummonedEntity entity;
    private final SummonBehaviour.Action.SpellCast config;

    // Spell registry entry — resolved lazily and cached (registry is stable at runtime)
    @Nullable private RegistryEntry<Spell> spellEntry = null;
    private boolean spellLookupAttempted = false;

    // Per-activation state
    private int castTick = 0;
    private int castDuration = 1;
    private boolean released = false;
    // How this activation aims, resolved once when the cast begins. Capturing it at begin() pins
    // the target for the whole cast, so a mid-cast retarget can't redirect the spell. Cleared in end().
    @Nullable private CastAim aim = null;
    // Channel state: the schedule-carrying process of a channeled activation (null otherwise),
    // declared on the entity for client presentation (beam, particles, sound).
    @Nullable private SpellCast.Process channel = null;
    private boolean channelFired = false;

    public MobCastController(SummonedEntity entity, SummonBehaviour.Action.SpellCast config) {
        this.entity = entity;
        this.config = config;
    }

    @Nullable
    private RegistryEntry<Spell> resolveSpell() {
        if (spellLookupAttempted) return spellEntry;
        spellLookupAttempted = true;
        var id = Identifier.of(config.spell_id);
        spellEntry = SpellRegistry.from(entity.getEntityWorld()).getEntry(id).orElse(null);
        return spellEntry;
    }

    // Resolved engagement distances. The `range.{min,max,preferred}` fields are FRACTIONS of the
    // spell's effective range (`SpellParameters.getRange`, which applies caster-level modifiers), so they
    // auto-scale per caster.
    private float spellRange(RegistryEntry<Spell> entry) {
        return SpellParameters.getRange(entity, entry);
    }
    private float effectiveMin(RegistryEntry<Spell> entry) {
        return config.range.min * spellRange(entry);
    }
    private float effectiveMax(RegistryEntry<Spell> entry) {
        return config.range.max * spellRange(entry);
    }
    private float effectivePreferred(RegistryEntry<Spell> entry) {
        return config.range.preferred * spellRange(entry);
    }

    // MARK: Lifecycle (driven by SpellCastGoal, or any other AI regime)

    public boolean canBegin() {
        var entry = resolveSpell();
        if (entry == null) return false;
        var spell = entry.value();
        if (spell.active == null) return false;           // ACTIVE spells only
        // CHARGE spells depend on player input (hold-to-release) and are silently skipped.
        // INSTANT, CASTING and CHANNEL all work — channels run on a server-owned schedule.
        if (spell.active.cast.type == Spell.Active.Cast.Type.CHARGE) return false;
        if (!entity.isActive()) return false;             // not in spawn/despawn phase
        if (entity.cooldownManager.isCoolingDown(entry)) return false;
        return resolveAim(entry) != null;                 // is there anything to fire at?
    }

    public void begin() {
        castTick = 0;
        released = false;
        var entry = spellEntry; // already resolved by canBegin()
        aim = resolveAim(entry);
        if (entry != null) {
            var spell = entry.value();
            if (SpellParameters.isChanneled(spell)) {
                // Channels have no wind-up: the cast IS the channel, running on a server-owned
                // schedule (the same Process machinery players use), declared on the entity so
                // clients render the beam and play the cast presentation.
                var details = SpellParameters.getCastTimeDetails(entity, spell);
                channel = new SpellCast.Process(entity, entry, null, details.speed(), details.length(), entity.getEntityWorld().getTime());
                channelFired = false;
                entity.declareCastProcess(channel);
                SoundHelper.playSound(entity.getEntityWorld(), entity, spell.active.cast.start_sound);
            } else {
                castDuration = SpellParameters.isInstant(spell)
                        ? 1
                        : SpellParameters.getCastTimeDetails(entity, spell).length();
                if (castDuration <= 0) castDuration = 1;
            }
        }
        entity.onSpellCastStarted(entity.pickVariant(config.cast_animation_variants));
        entity.setAttacking(true);
    }

    public boolean shouldContinue() {
        return !released && entity.isActive() && aim != null && aim.valid();
    }

    public void end() {
        entity.setAttacking(false);
        entity.getNavigation().stop();
        aim = null;
        if (channel != null) {
            entity.declareCastProcess(null);
            // An interrupted channel already dealt its per-tick output — cooldown still applies
            if (channelFired && !released && spellEntry != null) {
                applyCooldown(spellEntry, spellEntry.value());
            }
            channel = null;
            channelFired = false;
        }
        // Covers both the natural-end path and early cancellation. Idempotent.
        entity.onSpellCastEnded();
        // Only consult clear_conditions when the spell actually fired this activation.
        if (released) {
            entity.onActionCompleted(SummonBehaviour.Action.Type.SPELL_CAST, config.spell_id);
        }
    }

    public void tick() {
        if (released || aim == null) return;
        var entry = spellEntry;
        if (entry == null) return;
        aim.approach();                 // chase the subject (no-op when stationary)
        aim.orient();                   // face the way the spell must fire
        if (channel != null) {
            tickChannel(entry, entry.value());
            return;
        }
        if (aim.engaged()) castTick++;  // progress only while in range and visible
        if (castTick >= castDuration) {
            releaseSpell(entry, entry.value());
            released = true;
        }
    }

    /// Channel timeline: wall-clock schedule, matching player channels — no engagement pause;
    /// a broken line of sight just means the beam hits a wall and the raycast finds no targets.
    /// Aim aborts (target dead / out of max range) still end the channel via `shouldContinue`.
    private void tickChannel(RegistryEntry<Spell> entry, Spell spell) {
        var time = entity.getEntityWorld().getTime();
        if (channel.isDue(time)) {
            channel.markDue();
            aim.orient(); // final orient before the raycast reads the look vector
            fireChannelTick(entry, spell);
            channelFired = true;
        }
        if (channel.progress(time).ratio() >= 1F) {
            finishChannel(entry, spell);
            released = true;
        }
    }

    private void releaseSpell(RegistryEntry<Spell> entry, Spell spell) {
        if (entity.getEntityWorld().isClient()) return;
        // Final orient before the engine reads the look vector.
        aim.orient();
        performSpell(entry);
        entity.onSpellCastEnded();
        entity.onSpellReleased(entity.pickVariant(config.release_animation_variants), config.release_animation_duration);
        applyCooldown(entry, spell);
    }

    /// One tick of a channeled cast: the spell's output split across the channel's ticks,
    /// exactly like the player path — including the sub-tick interval compensation.
    private void fireChannelTick(RegistryEntry<Spell> spellEntry, Spell spell) {
        var world = entity.getEntityWorld();
        if (world.isClient()) return;
        var multiplier = SpellParameters.channelValueMultiplier(spell);
        var interval = channel.channelInterval(entity);
        if (interval < 1) {
            multiplier *= (1F / interval);
        }
        var context = ownerAdjusted(new SpellExecution.ImpactContext()
                .power(SpellPower.getSpellPower(spell.school, entity))
                .target(SpellIntents.focusMode(spell))
                .channeled(multiplier));
        SpellDelivery.resolveAndDeliver(world, entity, spellEntry, cappedTargets(spellEntry, spell), context,
                (completionArgs) -> {
                    if (completionArgs.success() && spell.active.cast.channelReleaseFx()) {
                        ReleaseFx.send(world, entity, spellEntry, 1F);
                    }
                });
    }

    /// Natural end of a channel: release FX (unless every tick already played it), release
    /// animation, cooldown. Impacts happened on the ticks — nothing fires here.
    private void finishChannel(RegistryEntry<Spell> entry, Spell spell) {
        if (!entity.getEntityWorld().isClient() && !spell.active.cast.channelReleaseFx()) {
            ReleaseFx.send(entity.getEntityWorld(), entity, entry, 1F);
        }
        entity.onSpellCastEnded();
        entity.onSpellReleased(entity.pickVariant(config.release_animation_variants), config.release_animation_duration);
        applyCooldown(entry, spell);
    }

    /// Cooldown: use spell's own duration if set, else fall back to config override (ticks)
    private void applyCooldown(RegistryEntry<Spell> entry, Spell spell) {
        int cooldownTicks;
        if (spell.cost.cooldown != null && spell.cost.cooldown.duration > 0) {
            cooldownTicks = Math.round(SpellParameters.getCooldownDuration(entity, entry) * 20F);
        } else {
            cooldownTicks = config.cooldown; // already in ticks; default = 20
        }
        if (cooldownTicks > 0) {
            entity.cooldownManager.set(entry, cooldownTicks);
        }
    }

    /// The cost-free server-side fire (formerly `SpellExecution.targetAndPerformSpell`):
    /// targeting from the entity's current rotation/position, then the full delivery pipeline.
    /// No player costs (ammo, exhaust, cooldown) — this controller manages its own cooldown.
    private void performSpell(RegistryEntry<Spell> spellEntry) {
        var world = entity.getEntityWorld();
        if (world.isClient()) return;
        var spell = spellEntry.value();
        if (spell.active == null) return;

        var context = ownerAdjusted(new SpellExecution.ImpactContext()
                .power(SpellPower.getSpellPower(spell.school, entity))
                .target(SpellIntents.focusMode(spell)));

        SpellDelivery.resolveAndDeliver(world, entity, spellEntry, cappedTargets(spellEntry, spell), context,
                (completionArgs) -> {
                    if (completionArgs.success()) {
                        // Summons never cast CHARGE spells, so the pitch shift never applies; pass full.
                        ReleaseFx.send(world, entity, spellEntry, 1F);
                    }
                });
    }

    /// A pet's helpful casts attribute to its owner.
    private SpellExecution.ImpactContext ownerAdjusted(SpellExecution.ImpactContext context) {
        if (!entity.isAttackable() && entity instanceof Tameable) {
            var owner = ((Tameable) entity).getOwner();
            if (owner != null) {
                return context.effectiveCaster(owner);
            }
        }
        return context;
    }

    /// Server-side targeting from the entity's current rotation/position, with the target cap
    /// applied (sorted by distance to the caster).
    private SpellTarget.SearchResult cappedTargets(RegistryEntry<Spell> spellEntry, Spell spell) {
        var targetResult = SpellTarget.findTargets(entity, spellEntry, SpellTarget.SearchResult.empty(), true);
        var targets = targetResult.entities();
        if (spell.target.cap > 0) {
            targets = targets.stream()
                    .sorted(Comparator.comparingDouble(t -> t.squaredDistanceTo(entity.getEntityPos())))
                    .limit(spell.target.cap)
                    .toList();
            targetResult = new SpellTarget.SearchResult(targets, targetResult.location());
        }
        return targetResult;
    }

    // MARK: Aim resolution

    /// Picks how this activation aims: the live target when targeting is enabled and a usable target
    /// sits inside the engagement band, otherwise the configured fallback. Null = nothing to fire at.
    @Nullable
    private CastAim resolveAim(RegistryEntry<Spell> entry) {
        if (config.aiming.accept_target && entry != null) {
            var target = entity.getTarget();
            if (target != null && target.isAlive() && withinBand(entry, target)) {
                return new TrackedAim(target);
            }
        }
        return switch (config.aiming.fallback) {
            case NONE    -> null;
            case FORWARD -> forwardAim;
            case SELF    -> selfAim;
        };
    }

    /// True when the target sits inside `[min, max] × effective range`.
    private boolean withinBand(RegistryEntry<Spell> entry, LivingEntity target) {
        double distSq = entity.squaredDistanceTo(target);
        float maxR = effectiveMax(entry);
        if (distSq > (double) maxR * maxR) return false;
        float minR = effectiveMin(entry);
        return minR <= 0 || distSq >= (double) minR * minR;
    }

    /// Per-activation aiming strategy. Each method answers one question the cast lifecycle asks.
    private interface CastAim {
        /// May the cast keep running? (subject still valid, or always for stationary)
        boolean valid();
        /// Move toward the subject. No-op for stationary fallbacks.
        void approach();
        /// May the cast counter advance this tick? (in range and visible, or always)
        boolean engaged();
        /// Rotate the entity so the spell fires the right way.
        void orient();
    }

    /// Tracks the entity's current target: navigates to `preferred × range`, follows line-of-sight,
    /// and locks rotation onto it. The target is captured for the whole cast.
    private class TrackedAim implements CastAim {
        private final LivingEntity target;
        private int seeingTicker = 0;
        private boolean inPreferredRange = false;

        private TrackedAim(LivingEntity target) { this.target = target; }

        @Override
        public boolean valid() {
            if (!target.isAlive()) return false;
            // Enforce the upper engagement edge mid-cast (a target leaving `max` aborts). The lower
            // edge is intentionally not re-checked.
            float maxR = effectiveMax(spellEntry);
            return entity.squaredDistanceTo(target) <= (double) maxR * maxR;
        }

        @Override
        public void approach() {
            float preferred = effectivePreferred(spellEntry);
            double preferredSq = (double) preferred * preferred;
            inPreferredRange = entity.squaredDistanceTo(target) <= preferredSq;
            boolean canSee = entity.getVisibilityCache().canSee(target);
            if (canSee) { if (seeingTicker < 10) seeingTicker++; }
            else        { if (seeingTicker > 0)  seeingTicker--; }
            // Close in until inside preferred range (or while sight is broken); then hold.
            if (!inPreferredRange || seeingTicker <= 0) {
                entity.getNavigation().startMovingTo(target, 1.0);
            } else {
                entity.getNavigation().stop();
            }
        }

        @Override
        public boolean engaged() { return inPreferredRange && seeingTicker > 0; }

        @Override
        public void orient() { entity.lockRotationTo(target); }
    }

    /// Shared base for stationary fallbacks: never chases, fires on cooldown, runs to release.
    private abstract class StationaryAim implements CastAim {
        @Override public boolean valid()   { return true; }
        @Override public void approach()   { }
        @Override public boolean engaged() { return true; }
    }

    /// Fires straight ahead along the entity's current (spawn-set) facing — turret.
    private final CastAim forwardAim = new StationaryAim() {
        @Override public void orient() { /* keep current facing */ }
    };

    /// Aims at the entity's own position (pitch straight down), so directional spells resolve at its
    /// feet; self-centred AoE spells are unaffected by rotation.
    private final CastAim selfAim = new StationaryAim() {
        @Override public void orient() {
            entity.setPitch(90F);
            entity.setHeadYaw(entity.getYaw());
            entity.setBodyYaw(entity.getYaw());
        }
    };
}
