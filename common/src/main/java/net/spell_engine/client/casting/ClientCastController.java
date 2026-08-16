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
import java.util.Objects;

/// Client-side counterpart of the server-side casting authority ({@link
/// net.spell_engine.internals.casting.SpellCastInteractor}). One instance per
/// `ClientPlayerEntity`, owned by its mixin and reachable via
/// {@link SpellCasterClient#getCastController()} — so all state resets naturally when vanilla
/// replaces the player entity on respawn or world join. Owns:
/// - the input POLICY of casting: what a key hold or release means per cast mode (start,
///   cancel, charge release; hold-to-cast vs toggle) — `SpellHotbar` owns the input MECHANICS
///   (key scanning, edge memory, vanilla arbitration) and forwards control events here;
/// - the cast process state machine: per-tick cancellation conditions, client-side targeting,
///   and — for mechanics not yet server-authoritative — the channel/release timing decisions;
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

    private void setProcess(@Nullable SpellCast.Process newValue, boolean sync) {
        var oldValue = predictedProcess;
        predictedProcess = newValue;
        if (sync && !Objects.equals(oldValue, newValue)) {
            Identifier id = null;
            float speed = 0;
            int length = 0;
            if (newValue != null) {
                id = newValue.spell().getKey().get().getValue();
                speed = newValue.speed();
                length = newValue.length();
            }
            Platform.util().networkC2S_Send(new Packets.SpellCastSync(id, speed, length));
        }
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
                cancelSpellCast(false);
            }
            var authoritative = SpellCast.serverAuthoritative(SpellCast.Mode.from(spell));
            var instant = SpellParameters.isInstantCast(spellEntry, player);
            if (instant) {
                if (authoritative) {
                    // One-shot targeting at the input frame, shipped with the request; no process
                    var targetResult = SpellTarget.findTargets(player, spellEntry, SpellTarget.SearchResult.empty(), SpellEngineClient.config.filterInvalidTargets);
                    Platform.util().networkC2S_Send(new Packets.CastRequest(spellId, snapshotFor(spellEntry, targetResult)));
                    applyInstantGlobalCooldown();
                } else {
                    // Release instant spell
                    var newProcess = new SpellCast.Process(player, spellEntry, itemStack.getItem(), 1, 0, player.getWorld().getTime());
                    this.setProcess(newProcess, false);
                    this.tick();
                    applyInstantGlobalCooldown();
                }
            } else {
                if (authoritative) {
                    // Predicted process only (display); the server starts its own on the request.
                    // Current aim rides along so the server has targets from tick one.
                    var details = SpellParameters.getCastTimeDetails(player, spell);
                    setProcess(new SpellCast.Process(player, spellEntry, itemStack.getItem(), details.speed(), details.length(), player.getWorld().getTime()), false);
                    spellTarget = SpellTarget.findTargets(player, spellEntry, SpellTarget.SearchResult.empty(), SpellEngineClient.config.filterInvalidTargets);
                    Platform.util().networkC2S_Send(new Packets.CastRequest(spellId, snapshotFor(spellEntry, spellTarget)));
                } else {
                    // Start casting
                    var details = SpellParameters.getCastTimeDetails(player, spell);
                    setProcess(new SpellCast.Process(player, spellEntry, itemStack.getItem(), details.speed(), details.length(), player.getWorld().getTime()), true);
                }
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
        cancelSpellCast(true);
    }

    public void cancelSpellCast(boolean syncProcess) {
        var process = this.predictedProcess;
        if (process != null) {
            var spell = process.spell().value();
            if (SpellCast.serverAuthoritative(SpellCast.Mode.from(spell))) {
                // End-input: the server decides what ending means for this mechanic
                // (cancel / early channel completion) — regardless of `syncProcess`, which only
                // ever concerned the legacy cast-sync channel.
                Platform.util().networkC2S_Send(new Packets.CastInput(process.id(), snapshotFor(process.spell(), spellTarget)));
                endPrediction();
                return;
            }
            if (SpellParameters.isChanneled(spell)) {
                var progress = process.progress(player.getWorld().getTime());
                Platform.util().networkC2S_Send(new Packets.SpellRequest(SpellCast.Action.RELEASE, process.id(), progress.ratio(), new int[]{}, null));
            }
        }
        setProcess(null, syncProcess);
        spellTarget = SpellTarget.SearchResult.empty();
    }

    /// Releases a CHARGED spell at its current progress (the charge ratio). Below the spell's
    /// `min_release_ratio` the cast fizzles instead.
    public void releaseCharge() {
        var process = this.predictedProcess;
        if (process == null) {
            return;
        }
        if (SpellCast.serverAuthoritative(SpellCast.Mode.from(process.spell().value()))) {
            // The server computes the ratio from its own clock and applies the min-ratio fizzle;
            // the end-input carries the release frame's targets (zero staleness).
            Platform.util().networkC2S_Send(new Packets.CastInput(process.id(), snapshotFor(process.spell(), spellTarget)));
            endPrediction();
            return;
        }
        var charge = process.spell().value().active.cast.charge;
        var progress = process.progress(player.getWorld().getTime());
        if (charge != null && progress.ratio() < charge.min_release_ratio) {
            cancelSpellCast(); // below the minimum charge — fizzle
            return;
        }
        releaseSpellCast(process, SpellCast.Action.RELEASE);
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
            var cast = spell.active.cast;
            spellTarget = SpellTarget.findTargets(player, process.spell(), spellTarget, SpellEngineClient.config.filterInvalidTargets);
            streamTargets(process);

            var mode = SpellCast.Mode.from(spell);
            if (SpellCast.serverAuthoritative(mode)) {
                // Prediction only — the server owns the timeline and fires on its own clock.
                var castTicks = process.spellCastTicksSoFar(player.getWorld().getTime());
                // Reconciliation: if the server never confirmed this cast (its declared process,
                // mirrored from tracked data, stays empty), the start was rejected server-side —
                // drop the prediction quietly.
                var declared = caster.getInteractor().process();
                if (declared == null && castTicks > RECONCILE_GRACE_TICKS) {
                    endPrediction();
                    return;
                }
                switch (mode) {
                    case CASTING -> {
                        if (castTicks >= process.length()) {
                            endPrediction();
                        }
                    }
                    case CHANNEL -> {
                        if (process.progress(player.getWorld().getTime()).ratio() >= 1F) {
                            endPrediction();
                        }
                    }
                    default -> { } // CHARGED holds until key-up
                }
                return;
            }

            if (SpellParameters.isChanneled(spell)) {
                if (process.isDue(player.getWorld().getTime())) {
                    process.markDue();
                    releaseSpellCast(process, SpellCast.Action.CHANNEL);
                }
                var progress = process.progress(player.getWorld().getTime());
                if (progress.ratio() >= 1) {
                    cancelSpellCast();
                }
            } else {
                var spellCastTicks = process.spellCastTicksSoFar(player.getWorld().getTime());
                var isFinished = spellCastTicks >= process.length();
                // CHARGE spells are not auto-released at full: the player may hold the charge
                // indefinitely and releases manually (key-up, via SpellHotbar -> releaseCharge).
                if (isFinished && cast.type != Spell.Active.Cast.Type.CHARGE) {
                    // Release spell
                    releaseSpellCast(process, SpellCast.Action.RELEASE);
                }
            }
        } else {
            spellTarget = SpellTarget.SearchResult.empty();
        }
    }

    private void releaseSpellCast(SpellCast.Process process, SpellCast.Action action) {
        var spellId = process.id();
        var progress = process.progress(player.getWorld().getTime());
        var targets = spellTarget.entities();
        var location = spellTarget.location();
        int[] targetIDs = new int[targets.size()];
        int i = 0;
        for (var target : targets) {
            targetIDs[i] = target.getId();
            i += 1;
        }

        Platform.util().networkC2S_Send(new Packets.SpellRequest(action, spellId, progress.ratio(), targetIDs, location));
        switch (action) {
            case CHANNEL -> {
                if (progress.ratio() >= 1) {
                    cancelSpellCast();
                }
            }
            case RELEASE -> {
                cancelSpellCast();
            }
        }
    }

    // MARK: Server-authoritative support (Phase D)

    /// How long a predicted cast survives without the server confirming it (declared process
    /// still empty) before the prediction is dropped as rejected. Generous enough for high ping;
    /// the local pre-flight `attempt` catches nearly all denials before a packet is even sent.
    private static final int RECONCILE_GRACE_TICKS = 20;

    /// Ends the predicted cast without any packets — the server's own timeline already ran
    /// (or never confirmed) the real thing.
    private void endPrediction() {
        setProcess(null, false);
        spellTarget = SpellTarget.SearchResult.empty();
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

    @Nullable private SpellCast.TargetSnapshot lastSentSnapshot = null;
    @Nullable private Identifier lastStreamSpellId = null;
    private int streamSequence = 0;

    /// Replicates the cursor targeting to the server as STATE ("this is what my cursor selects
    /// right now"), every tick IF CHANGED, only while a cursor-driven cast is active. The
    /// server's authoritative fires consume the latest snapshot.
    private void streamTargets(SpellCast.Process process) {
        var option = SpellCast.Option.of(process.spell());
        if (!option.targeting().clientResolved()) {
            return;
        }
        var spellId = process.id();
        if (!spellId.equals(lastStreamSpellId)) {
            lastStreamSpellId = spellId;
            lastSentSnapshot = null;
            streamSequence = 0;
        }
        var snapshot = SpellCast.TargetSnapshot.of(spellTarget);
        if (snapshot.equals(lastSentSnapshot)) {
            return; // Unchanged aim — silence means "unchanged", the server's copy stays valid
        }
        streamSequence += 1;
        lastSentSnapshot = snapshot;
        Platform.util().networkC2S_Send(new Packets.TargetStream(spellId, snapshot, streamSequence));
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
