package net.spell_engine.entity.goal;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.api.spell.summon.SummonBehaviour;
import net.spell_engine.entity.SummonedEntity;
import net.spell_engine.internals.SpellHelper;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/// Casts a configured spell on cooldown. Aiming is resolved once per activation into a {@link CastAim}
/// (a tracked target, or a stationary forward/self fallback), so the cast lifecycle stays free of
/// mode checks.
public class SpellCastGoal extends Goal {
    private final SummonedEntity entity;
    private final SummonBehaviour.Action.SpellCast config;

    // Spell registry entry — resolved lazily and cached (registry is stable at runtime)
    @Nullable private RegistryEntry<Spell> spellEntry = null;
    private boolean spellLookupAttempted = false;

    // Per-activation state
    private int castTick = 0;
    private int castDuration = 1;
    private boolean released = false;
    // How this activation aims, resolved once when the cast begins. Capturing it at start() pins
    // the target for the whole cast, so a mid-cast retarget can't redirect the spell. Cleared in stop().
    @Nullable private CastAim aim = null;

    public SpellCastGoal(SummonedEntity entity, SummonBehaviour.Action.SpellCast config) {
        this.entity = entity;
        this.config = config;
        setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Nullable
    private RegistryEntry<Spell> resolveSpell() {
        if (spellLookupAttempted) return spellEntry;
        spellLookupAttempted = true;
        var id = Identifier.of(config.spell_id);
        spellEntry = SpellRegistry.from(entity.getWorld()).getEntry(id).orElse(null);
        return spellEntry;
    }

    // Resolved engagement distances. The `range.{min,max,preferred}` fields are FRACTIONS of the
    // spell's effective range (`SpellHelper.getRange`, which applies caster-level modifiers), so they
    // auto-scale per caster.
    private float spellRange(RegistryEntry<Spell> entry) {
        return SpellHelper.getRange(entity, entry);
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

    @Override
    public boolean shouldRunEveryTick() { return true; }

    @Override
    public boolean canStart() {
        var entry = resolveSpell();
        if (entry == null) return false;
        var spell = entry.value();
        if (spell.active == null) return false;           // ACTIVE spells only
        // Summons only cast INSTANT and CASTING spells. Channeled and charged spells depend on
        // player-input timing (per-tick channeling / hold-to-release), so they are silently skipped.
        if (SpellHelper.isChanneled(spell)) return false;
        if (spell.active.cast.resolvedType() == Spell.Active.Cast.Type.CHARGE) return false;
        if (!entity.isActive()) return false;             // not in spawn/despawn phase
        if (entity.cooldownManager.isCoolingDown(entry)) return false;
        return resolveAim(entry) != null;                 // is there anything to fire at?
    }

    @Override
    public void start() {
        castTick = 0;
        released = false;
        var entry = spellEntry; // already resolved by canStart()
        aim = resolveAim(entry);
        if (entry != null) {
            var spell = entry.value();
            castDuration = SpellHelper.isInstant(spell)
                    ? 1
                    : SpellHelper.getCastTimeDetails(entity, spell).length();
            if (castDuration <= 0) castDuration = 1;
        }
        entity.onSpellCastStarted(entity.pickVariant(config.cast_animation_variants));
        entity.setAttacking(true);
    }

    @Override
    public boolean shouldContinue() {
        return !released && entity.isActive() && aim != null && aim.valid();
    }

    @Override
    public void stop() {
        entity.setAttacking(false);
        entity.getNavigation().stop();
        aim = null;
        // Covers both the natural-end path and early cancellation. Idempotent.
        entity.onSpellCastEnded();
        // Only consult clear_conditions when the spell actually fired this activation.
        if (released) {
            entity.onActionCompleted(SummonBehaviour.Action.Type.SPELL_CAST, config.spell_id);
        }
    }

    @Override
    public void tick() {
        if (released || aim == null) return;
        var entry = spellEntry;
        if (entry == null) return;
        aim.approach();                 // chase the subject (no-op when stationary)
        aim.orient();                   // face the way the spell must fire
        if (aim.engaged()) castTick++;  // progress only while in range and visible
        if (castTick >= castDuration) {
            releaseSpell(entry, entry.value());
            released = true;
        }
    }

    private void releaseSpell(RegistryEntry<Spell> entry, Spell spell) {
        if (entity.getWorld().isClient()) return;
        // Final orient before the engine reads the look vector.
        aim.orient();
        SpellHelper.targetAndPerformSpell(entity.getWorld(), entity, entry);
        entity.onSpellCastEnded();
        entity.onSpellReleased(entity.pickVariant(config.release_animation_variants), config.release_animation_duration);

        // Cooldown: use spell's own duration if set, else fall back to config override (ticks)
        int cooldownTicks;
        if (spell.cost.cooldown != null && spell.cost.cooldown.duration > 0) {
            cooldownTicks = Math.round(SpellHelper.getCooldownDuration(entity, entry) * 20F);
        } else {
            cooldownTicks = config.cooldown; // already in ticks; default = 20
        }
        if (cooldownTicks > 0) {
            entity.cooldownManager.set(entry, cooldownTicks);
        }
    }

    // --- Aim resolution ---

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
        /// May the goal keep running? (subject still valid, or always for stationary)
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
