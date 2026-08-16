package net.spell_engine.client.casting;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.spell_engine.Platform;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.effect.EntityActionsAllowed;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.client.input.SpellHotbar;
import net.spell_engine.internals.SpellParameters;
import net.spell_engine.internals.casting.SpellCast;
import net.spell_engine.internals.casting.SpellCasterClient;
import net.spell_engine.internals.casting.SpellCasting;
import net.spell_engine.internals.target.SpellTarget;
import net.spell_engine.network.Packets;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/// Client-side counterpart of the server-side casting authority ({@link
/// net.spell_engine.internals.casting.SpellCastInteractor}). One instance per
/// `ClientPlayerEntity`, owned by its mixin and reachable via
/// {@link SpellCasterClient#getCastController()} — so all state resets naturally when vanilla
/// replaces the player entity on respawn or world join. Owns:
/// - the input POLICY of casting: what a key hold or release means per cast mode (start,
///   cancel, charge release; hold-to-cast vs toggle) — `SpellHotbar` owns the input MECHANICS
///   (key scanning, edge memory, vanilla arbitration) and forwards control events here;
/// - the predicted cast process: per-tick cancellation conditions, client-side targeting, and
///   reconciliation against the server's declared process (the timeline authority);
/// - the target stream: latest-wins replication of the cursor targeting to the server, sent
///   every tick IF CHANGED while a cursor-driven cast is active, consumed by the server's
///   authoritative fires.
public class ClientCastController {
    private final ClientPlayerEntity player;

    public ClientCastController(ClientPlayerEntity player) {
        this.player = player;
    }

    // MARK: Predicted cast process
    //
    // This is deliberately a SECOND process state next to the interactor's: the prediction is
    // born at the input frame (instant cast bar), while the server's authoritative process —
    // mirrored into the interactor via tracked data — arrives a round trip later. The two are
    // different truths with one source each; reconciliation in `tick` compares them. For
    // mechanics still on the legacy protocol this state is not a mere prediction — it drives
    // the cast — which ends with Phase F.

    @Nullable private SpellCast.Process predictedProcess = null;
    private SpellTarget.SearchResult spellTarget = SpellTarget.SearchResult.empty();

    @Nullable public SpellCast.Process predictedProcess() {
        return predictedProcess;
    }

    public List<Entity> currentTargets() {
        var targets = spellTarget.entities();
        if (targets == null) {
            return List.of();
        }
        return targets;
    }

    @Nullable public Entity currentFirstTarget() {
        return spellTarget.entities().stream().findFirst().orElse(null);
    }

    @Nullable public SpellCast.Progress progress() {
        if (predictedProcess != null) {
            return predictedProcess.progress(player.getWorld().getTime());
        }
        return null;
    }

    private void setProcess(@Nullable SpellCast.Process newValue) {
        predictedProcess = newValue;
    }

    // MARK: Cast lifecycle

    public SpellCast.Attempt startSpellCast(ItemStack itemStack, RegistryEntry<Spell> spellEntry) {
        if (player.isSpectator()) {
            return SpellCast.Attempt.none();
        }
        if (spellEntry == null) {
            this.cancelSpellCast();
            return SpellCast.Attempt.none();
        }
        var spell = spellEntry.value();
        var spellId = spellEntry.getKey().get().getValue();
        if ((predictedProcess != null && predictedProcess.id().equals(spellId))
                || spell == null) {
            return SpellCast.Attempt.none();
        }
        if (EntityActionsAllowed.isImpaired(player, EntityActionsAllowed.Player.CAST_SPELL, true)) {
            return SpellCast.Attempt.none();
        }
        var attempt = SpellCasting.attempt(player, itemStack, spellId);
        if (attempt.isSuccess()) {
            if (predictedProcess != null) {
                // Cancel previous spell
                cancelSpellCast();
            }
            var instant = SpellParameters.isInstantCast(spellEntry, player);
            if (instant) {
                // One-shot targeting at the input frame, shipped with the request; no process
                var targetResult = SpellTarget.findTargets(player, spellEntry, SpellTarget.SearchResult.empty(), SpellEngineClient.config.filterInvalidTargets);
                Platform.util().networkC2S_Send(new Packets.CastRequest(spellId, snapshotFor(spellEntry, targetResult)));
                applyInstantGlobalCooldown();
            } else {
                // Predicted process only (display); the server starts its own on the request.
                // Current aim rides along so the server has targets from tick one.
                var details = SpellParameters.getCastTimeDetails(player, spell);
                setProcess(new SpellCast.Process(player, spellEntry, itemStack.getItem(), details.speed(), details.length(), player.getWorld().getTime()));
                predictionConfirmed = false;
                spellTarget = SpellTarget.findTargets(player, spellEntry, SpellTarget.SearchResult.empty(), SpellEngineClient.config.filterInvalidTargets);
                var snapshot = snapshotFor(spellEntry, spellTarget);
                lastSentSnapshot = snapshot; // the request seeds the server's slot; the stream dedups against it
                Platform.util().networkC2S_Send(new Packets.CastRequest(spellId, snapshot));
            }
        }
        return attempt;
    }

    private void applyInstantGlobalCooldown() {
        var duration = SpellEngineMod.config.spell_instant_cast_global_cooldown;
        if (duration > 0) {
            for (var slot: SpellHotbar.INSTANCE.slots) {
                var spellEntry = slot.spell();
                if (spellEntry == null) {
                    // Some slots may not have spells (such as item usage bypass slot)
                    continue;
                }
                var spell = spellEntry.value();
                if (spell.active != null && spell.active.cast != null && spell.active.cast.duration <= 0) {
                    ((SpellCasterClient) player).getCooldownManager().set(spellEntry, duration, false);
                }
            }
        }
    }

    public void cancelSpellCast() {
        var process = this.predictedProcess;
        if (process != null) {
            // End-input: the server decides what ending means for this mechanic
            // (cancel / early channel completion / charge release).
            Platform.util().networkC2S_Send(new Packets.CastInput(process.id(), snapshotFor(process.spell(), spellTarget)));
        }
        endPrediction();
    }

    /// Releases a CHARGED spell. The server computes the ratio from its own clock and applies
    /// the min-ratio fizzle; the end-input carries the release frame's targets (zero staleness).
    public void releaseCharge() {
        var process = this.predictedProcess;
        if (process == null) {
            return;
        }
        Platform.util().networkC2S_Send(new Packets.CastInput(process.id(), snapshotFor(process.spell(), spellTarget)));
        endPrediction();
    }

    /// Per-tick heartbeat of the cast: cancellation conditions, client-side targeting +
    /// streaming, prediction reconciliation — and, for mechanics not yet server-authoritative,
    /// the legacy channel/release timing decisions.
    public void tick() {
        var process = this.predictedProcess;
        if (process != null) {
            var caster = (SpellCasterClient) player;
            if (!player.isAlive()
                    || player.getMainHandStack().getItem() != process.item()
                    || caster.getCooldownManager().isCoolingDown(process.spell())
                    || EntityActionsAllowed.isImpaired(player, EntityActionsAllowed.Player.CAST_SPELL, true)
            ) {
                cancelSpellCast();
                return;
            }
            var spell = process.spell().value();
            spellTarget = SpellTarget.findTargets(player, process.spell(), spellTarget, SpellEngineClient.config.filterInvalidTargets);
            streamTargets(process);

            // Prediction only — the server owns the timeline and fires on its own clock.
            var mode = SpellCast.Mode.from(spell);
            var castTicks = process.spellCastTicksSoFar(player.getWorld().getTime());
            var declared = caster.getInteractor().process();
            var declaredMatches = declared != null && declared.id().equals(process.id());
            if (declaredMatches) {
                predictionConfirmed = true;
            }
            if (predictionConfirmed) {
                // The prediction ends when the SERVER'S declared process ends (it fired,
                // completed or cancelled) — never at client-computed completion. The client
                // clock starts at the input frame, the server's ~latency later; ending here
                // at client completion let a held-key re-cast supersede a server process one
                // tick away from firing (release swallowed, cast "restarts"). (Bug#1)
                if (!declaredMatches) {
                    endPrediction();
                    return;
                }
            } else if (castTicks > RECONCILE_GRACE_TICKS) {
                endPrediction(); // the server never confirmed this cast — rejected
                return;
            }
            // Fallback: end an overrun prediction even if the declaration was lost
            if (mode != SpellCast.Mode.CHARGED
                    && castTicks >= process.length() + RECONCILE_GRACE_TICKS) {
                endPrediction();
            }
        } else {
            spellTarget = SpellTarget.SearchResult.empty();
        }
    }

    // MARK: Prediction support

    /// How long a predicted cast survives without the server confirming it (declared process
    /// still empty) before the prediction is dropped as rejected. Generous enough for high ping;
    /// the local pre-flight `attempt` catches nearly all denials before a packet is even sent.
    private static final int RECONCILE_GRACE_TICKS = 20;

    /// Whether the server has confirmed the current prediction (its declared process matched
    /// at least once). Gates the "declaration ended → prediction ends" reconciliation rule.
    private boolean predictionConfirmed = false;

    /// Ends the predicted cast without any packets — the server's own timeline already ran
    /// (or never confirmed) the real thing.
    private void endPrediction() {
        setProcess(null);
        spellTarget = SpellTarget.SearchResult.empty();
        predictionConfirmed = false;
    }

    /// The snapshot to ship for this spell: the current targeting for cursor-driven shapes,
    /// EMPTY for server-resolved shapes (whose payloads the server would reject anyway).
    private static SpellCast.TargetSnapshot snapshotFor(RegistryEntry<Spell> spellEntry, SpellTarget.SearchResult targetResult) {
        var option = SpellCast.Option.of(spellEntry);
        return option.targeting().clientResolved()
                ? SpellCast.TargetSnapshot.of(targetResult)
                : SpellCast.TargetSnapshot.EMPTY;
    }

    // MARK: Target stream

    /// The last snapshot shipped to the server (via CastRequest or TargetStream), for on-change
    /// suppression. Seeded by each cast request, so every cast starts with a clean dedup slate.
    @Nullable private SpellCast.TargetSnapshot lastSentSnapshot = null;

    /// Replicates the cursor targeting to the server as STATE ("this is what my cursor selects
    /// right now"), every tick IF CHANGED, only while a cursor-driven cast is active. The
    /// server's authoritative fires consume the latest snapshot.
    private void streamTargets(SpellCast.Process process) {
        var option = SpellCast.Option.of(process.spell());
        if (!option.targeting().clientResolved()) {
            return;
        }
        var snapshot = SpellCast.TargetSnapshot.of(spellTarget);
        if (snapshot.equals(lastSentSnapshot)) {
            return; // Unchanged aim — silence means "unchanged", the server's copy stays valid
        }
        lastSentSnapshot = snapshot;
        Platform.util().networkC2S_Send(new Packets.TargetStream(process.id(), snapshot));
    }

    // MARK: Input policy

    /// Outcome of a control event. The hotbar uses it to update its edge memory (debounce) and
    /// to build the input `Handle` it reports upstream.
    public record Reaction(Type type, @Nullable SpellCast.Attempt attempt) {
        public enum Type {
            NONE,      /// The event required no process control
            STARTED,   /// A cast attempt was made (it may still have failed — see `attempt`)
            STOPPED,   /// An ongoing cast was cancelled
            RELEASED   /// An ongoing charge was released
        }
        public static final Reaction NOTHING = new Reaction(Type.NONE, null);
    }

    /// A slot's key is held down this tick (called every such tick, not only on the down edge —
    /// holding a key re-casts once the previous cast ends, and re-attempts instants as their
    /// cooldown expires). `freshForStart`/`freshForStop` report whether this continuous hold has
    /// not yet started/stopped a cast — the edge memory itself stays in the hotbar.
    public Reaction keyHeld(SpellCast.Option option, boolean freshForStart, boolean freshForStop) {
        switch (option.mode()) {
            case INSTANT -> {
                var attempt = startSpellCast(player.getMainHandStack(), option.spell());
                return new Reaction(Reaction.Type.STARTED, attempt);
            }
            case CASTING, CHANNEL -> {
                if (isCastingSame(option)) {
                    if (!holdToCast(option) && freshForStop) {
                        cancelSpellCast();
                        return new Reaction(Reaction.Type.STOPPED, null);
                    }
                } else if (freshForStart) {
                    var attempt = startSpellCast(player.getMainHandStack(), option.spell());
                    return new Reaction(Reaction.Type.STARTED, attempt);
                }
            }
            case CHARGED -> {
                if (isCastingSame(option)) {
                    if (!SpellEngineClient.config.holdToCastCharged && freshForStop) {
                        releaseCharge();
                        return new Reaction(Reaction.Type.RELEASED, null);
                    }
                } else if (freshForStart) {
                    var attempt = startSpellCast(player.getMainHandStack(), option.spell());
                    return new Reaction(Reaction.Type.STARTED, attempt);
                }
            }
            case PASSIVE, ITEM_USE -> { }
        }
        return Reaction.NOTHING;
    }

    /// A slot's key is up this tick (called every such tick). Ends hold-to-cast casts of this
    /// option's spell. Returns true when the event ended a cast, so the hotbar treats it as
    /// handled input.
    public boolean keyUp(SpellCast.Option option) {
        if (!isCastingSame(option)) { return false; }
        switch (option.mode()) {
            case CASTING, CHANNEL -> {
                if (holdToCast(option)) {
                    cancelSpellCast();
                    return true;
                }
            }
            case CHARGED -> {
                if (SpellEngineClient.config.holdToCastCharged) {
                    releaseCharge();
                    return true;
                }
            }
            default -> { }
        }
        return false;
    }

    private boolean isCastingSame(SpellCast.Option option) {
        return predictedProcess != null && predictedProcess.id().equals(option.id());
    }

    /// Hold-to-cast (true) vs toggle (false) for timed casts.
    /// (Non-channeled timed casts follow the charged spells' setting — preserved legacy behavior.)
    private static boolean holdToCast(SpellCast.Option option) {
        return option.mode() == SpellCast.Mode.CHANNEL
                ? SpellEngineClient.config.holdToCastChannelled
                : SpellEngineClient.config.holdToCastCharged;
    }
}
