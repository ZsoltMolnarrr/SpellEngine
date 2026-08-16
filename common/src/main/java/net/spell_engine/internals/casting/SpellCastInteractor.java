package net.spell_engine.internals.casting;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.internals.SpellExecution;
import net.spell_engine.internals.SpellParameters;
import net.spell_engine.internals.container.SpellContainerSource;
import net.spell_engine.internals.target.SpellTarget;
import net.spell_engine.utils.SoundHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/// The server-side casting authority component of a caster: owns the caster's castable
/// {@link SpellCast.Option}s and the {@link SpellCast.Process}, and receives control signals
/// (from network handlers for players; from `MobCastController` for mobs, later). Drivers cannot
/// be told apart from in here — no signal carries packet or input types.
///
/// Entity-specific behavior is injected via {@link Ports} (lambdas assembled by a factory such as
/// {@link #forPlayer}), so the same component serves players and mobs.
///
/// Server-side casting rework, Phase B+ (strangler fig): the legacy client-driven protocol calls
/// through this class, which accumulates the gate, state and fire logic while the client remains
/// in charge of timing. Authority flips per cast mechanic in Phase D, at which point
/// {@link #requestCast} / {@link #requestEnd} become the real entry points and
/// {@link #performRequested} retires together with the legacy packets.
public class SpellCastInteractor {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    /// Entity-specific wiring, injected at construction. Every component is a lambda so hosts
    /// compose only what they have; this record names the full contract in one place.
    /// (Phase D/E additions land here: cost policy, server-side target resolution, animations.)
    public record Ports(
            /// Recomputes the caster's current options (player: from spell containers;
            /// mob: from its behaviour config). Invoked on {@link #invalidateOptions}.
            Supplier<List<SpellCast.Option>> resolveOptions,
            /// Declares the cast process to interested clients (player: tracked data — the
            /// caster's own client reconciles its prediction, observers render the cast).
            /// Must no-op on the client side.
            Consumer<SpellCast.Process> declareProcess,
            /// Declares the caster's options to interested clients (player: tracked data,
            /// consumed by the owner's hotbar). Must no-op on the client side.
            Consumer<List<SpellCast.Option>> declareOptions
    ) { }

    /// Assembles the player wiring. The declaration lambdas are supplied by the caller
    /// (`PlayerEntityMixin`), since the tracked data slots live there.
    public static SpellCastInteractor forPlayer(PlayerEntity player,
                                                Consumer<SpellCast.Process> declareProcess,
                                                Consumer<List<SpellCast.Option>> declareOptions) {
        return new SpellCastInteractor(player, new Ports(
                () -> SpellCast.Option.allOf(player),
                declareProcess,
                declareOptions
        ));
    }

    /// Used directly by legacy strangler internals (attempt gate, perform, sounds) — these
    /// migrate behind ports in Phase E, when mobs join.
    private final PlayerEntity player;
    private final Ports ports;

    public SpellCastInteractor(PlayerEntity player, Ports ports) {
        this.player = player;
        this.ports = ports;
    }

    // MARK: Owned state — options

    /// The caster's castable options. On the server: authoritative, recomputed via
    /// {@link Ports#resolveOptions} whenever containers change. On the client: a mirror,
    /// set from the synced tracked data via {@link #mirrorOptions}.
    private List<SpellCast.Option> options = List.of();

    public List<SpellCast.Option> options() {
        return options;
    }

    /// Server-side: containers changed — recompute, and re-declare when the result differs.
    /// (Called from `SpellContainerSource.update` when a fresh Result is stored.)
    public void invalidateOptions() {
        var computed = ports.resolveOptions().get();
        if (!computed.equals(options)) {
            options = computed;
            ports.declareOptions().accept(computed);
        }
    }

    /// Client-side: store the options declared by the server (no recompute, no re-declare).
    public void mirrorOptions(List<SpellCast.Option> synced) {
        options = synced;
    }

    // MARK: Owned state — cast process

    @Nullable private SpellCast.Process process = null;

    @Nullable public SpellCast.Process process() {
        return process;
    }

    /// Single write path for the process, server and client mirror alike: stores, then declares
    /// via the port (which no-ops on the client).
    public void setProcess(@Nullable SpellCast.Process process) {
        if (process != null && process.spell().value().active == null) { return; }
        this.process = process;
        ports.declareProcess().accept(process);
    }

    // MARK: Signals — cast lifecycle

    /// [CAST-start] Starts the casting process for a timed spell.
    /// Legacy-driven: `speed`/`length` arrive from the client's `SpellCastSync` packet.
    /// Phase D1 replaces them with server-computed cast time details.
    public void requestStart(Identifier spellId, float speed, int length) {
        var spellEntry = SpellRegistry.from(player.getWorld()).getEntry(spellId).orElse(null);
        if (spellEntry == null) {
            return;
        }
        var spell = spellEntry.value();
        if (spell.active == null) {
            return;
        }
        if (SpellCast.serverAuthoritative(SpellCast.Mode.from(spell))) {
            // The new protocol owns this mechanic ({@link #requestCast}); accepting a legacy
            // start here would let client-declared timing seed the authoritative timeline.
            return;
        }
        var itemStack = player.getMainHandStack();
        var attempt = SpellCasting.attempt(player, itemStack, spellId);
        if (!attempt.isSuccess()) {
            return;
        }
        // Allow clients to specify their haste without validation
        // var details = SpellParameters.getCastTimeDetails(player, spell);
        var process = new SpellCast.Process(player, spellEntry, itemStack.getItem(), speed, length, player.getWorld().getTime());
        // Every channel starts counting from zero. `clearCasting` resets too, but it is not reached
        // on every path: cancelling a cast to start another one (`cancelSpellCast(false)`) sends no
        // cast sync packet, leaving only the RELEASE request, which early-returns when
        // `attemptCasting` fails. Resetting here makes a stale index impossible to inherit.
        ((SpellCasterEntity) player).setChannelTickIndex(0);
        SpellCastSyncHelper.setCasting(player, process);
        SoundHelper.playSound(player.getWorld(), player, spell.active.cast.start_sound);
    }

    /// [CAST-clear] Ends the casting process without firing (client cancelled, or sync said so).
    public void requestClear() {
        SpellCastSyncHelper.clearCasting(player);
    }

    /// [FIRE-legacy] Performs the spell with an externally supplied targeting result — the legacy
    /// protocol, where the client decides when this happens and ships its targets in the packet.
    /// Only serves mechanics whose server-authority flag is off (plus TRIGGER, which carries
    /// pre-resolved targets and has no timeline); for flag-on mechanics the request is dropped,
    /// otherwise a stale or hostile legacy packet would fire a second time.
    public void performRequested(RegistryEntry<Spell> spellEntry, SpellTarget.SearchResult targetResult, SpellCast.Action action, float progress) {
        if (action != SpellCast.Action.TRIGGER
                && SpellCast.serverAuthoritative(SpellCast.Mode.from(spellEntry.value()))) {
            return;
        }
        logTargetDivergence(spellEntry, targetResult, action);
        SpellExecution.performSpell(player.getWorld(), player, spellEntry, targetResult, action, progress);
    }

    // MARK: Server-authoritative timeline (Phase D)

    /// [FIRE] Performs the spell with server-resolved targets: cursor-driven shapes rehydrate the
    /// latest streamed snapshot; server-resolved shapes (SELF / AROUND_CASTER / NONE) run the same
    /// server-side lookup the mob path uses.
    private void fire(RegistryEntry<Spell> spellEntry, SpellCast.Action action, float progress) {
        SpellExecution.performSpell(player.getWorld(), player, spellEntry, resolveTargets(spellEntry), action, progress);
    }

    private SpellTarget.SearchResult resolveTargets(RegistryEntry<Spell> spellEntry) {
        var option = SpellCast.Option.of(spellEntry);
        if (!option.targeting().clientResolved()) {
            return SpellTarget.findTargets(player, spellEntry, SpellTarget.SearchResult.empty(), true);
        }
        var snapshot = latestSnapshot(spellEntry.getKey().get().getValue());
        if (snapshot == null || !(player.getWorld() instanceof ServerWorld serverWorld)) {
            return SpellTarget.SearchResult.empty();
        }
        List<Entity> targets = new ArrayList<>();
        for (var targetId : snapshot.entityIds()) {
            var entity = serverWorld.getDragonPart(targetId); // `getEntityById` + dragon parts
            if (entity != null && !entity.isRemoved()) {
                targets.add(entity);
            }
        }
        return new SpellTarget.SearchResult(targets, snapshot.location());
    }

    /// Phase C3 measurement instrument, removed when Phase D flips authority: compares the
    /// targets the legacy request shipped (exactly-at-fire, the old design's freshness) against
    /// the streamed snapshot the server would fire with instead. Divergence here IS the staleness
    /// cost of server-initiated fires — the one open functional question of the rework.
    private void logTargetDivergence(RegistryEntry<Spell> spellEntry, SpellTarget.SearchResult targetResult, SpellCast.Action action) {
        var spellId = spellEntry.getKey().get().getValue();
        var snapshot = latestSnapshot(spellId);
        if (snapshot == null) {
            return; // No stream for this spell (server-resolved targeting, or client not streaming)
        }
        var requestedIds = targetResult.entities().stream().map(net.minecraft.entity.Entity::getId).toList();
        if (!requestedIds.equals(snapshot.entityIds())) {
            LOGGER.info("Spell cast target divergence — spell: {} action: {} request: {} stream: {}",
                    spellId, action, requestedIds, snapshot.entityIds());
        }
    }

    /// Server tick. For server-authoritative mechanics this IS the cast timeline: cancellation
    /// conditions, completion fire (CASTING), channel cadence (CHANNEL). CHARGE holds until its
    /// end-input. For mechanics still on the legacy protocol, only the overrun guard runs.
    public void tick() {
        var process = this.process;
        if (process == null) {
            return;
        }
        var spell = process.spell().value();
        var mode = SpellCast.Mode.from(spell);
        var time = player.getWorld().getTime();

        if (!SpellCast.serverAuthoritative(mode)) {
            // Legacy overrun guard: a client that stopped talking mid-cast. CHARGE spells may be
            // held at full charge indefinitely, so they are exempt (otherwise their cast state
            // would be cleared while still held).
            var isCharge = spell.active != null
                    && spell.active.cast.type == Spell.Active.Cast.Type.CHARGE;
            if (!isCharge && process.spellCastTicksSoFar(time) >= (process.length() * 1.5)) {
                SpellCastSyncHelper.clearCasting(player);
            }
            return;
        }

        // Cancellation conditions, server-side. The client predicts the same and also sends an
        // end-input, but the authority must not depend on that packet arriving in time.
        if (!player.isAlive()
                || player.getMainHandStack().getItem() != process.item()
                || ((SpellCasterEntity) player).getCooldownManager().isCoolingDown(process.spell())) {
            requestClear();
            return;
        }

        switch (mode) {
            case CASTING -> {
                if (process.spellCastTicksSoFar(time) >= process.length()) {
                    fire(process.spell(), SpellCast.Action.RELEASE, 1F);
                    if (this.process == process) {
                        // performSpell clears the process on RELEASE; this covers gate failures
                        requestClear();
                    }
                }
            }
            case CHANNEL -> {
                if (process.isDue(time)) {
                    process.markDue();
                    fire(process.spell(), SpellCast.Action.CHANNEL, process.progress(time).ratio());
                }
                if (this.process == process && process.progress(time).ratio() >= 1F) {
                    // Channel ran to completion: the final RELEASE settles cost (no impact)
                    fire(process.spell(), SpellCast.Action.RELEASE, 1F);
                    if (this.process == process) {
                        requestClear();
                    }
                }
            }
            case CHARGED -> {
                // Held indefinitely; fires (or fizzles) on the end-input — see requestEnd
            }
            default -> { }
        }
    }

    // MARK: Signals — new protocol (registered in Phase B, driven from Phase D)

    /// The latest client-submitted targeting state ("what my cursor selects right now"), kept as
    /// a single latest-wins slot. Consumed by server-initiated fires from Phase D on; until then
    /// it feeds the divergence telemetry above.
    @Nullable private SpellCast.TargetSnapshot latestSnapshot = null;
    @Nullable private Identifier latestSnapshotSpellId = null;
    private int latestSnapshotSequence = -1;

    /// [CAST-request] The unified start signal of the new protocol: begin casting an option.
    /// Instants carry their targeting snapshot and fire immediately; timed casts start the
    /// server-owned process (server-computed timing) and are fed by the target stream.
    public void requestCast(Identifier spellId, SpellCast.TargetSnapshot snapshot) {
        var spellEntry = SpellRegistry.from(player.getWorld()).getEntry(spellId).orElse(null);
        if (spellEntry == null) {
            return;
        }
        var spell = spellEntry.value();
        if (spell.active == null) {
            return;
        }
        if (!SpellCast.serverAuthoritative(SpellCast.Mode.from(spell))) {
            // Mechanic still on the legacy protocol — keep the snapshot, ignore the signal
            submitTargets(spellId, snapshot, latestSnapshotSequence + 1);
            return;
        }
        // Possession gate: the spell must actually be granted to this caster
        if (SpellContainerSource.getFirstSourceOfSpell(spellId, player) == null) {
            return;
        }
        var itemStack = player.getMainHandStack();
        var attempt = SpellCasting.attempt(player, itemStack, spellId);
        if (!attempt.isSuccess()) {
            return;
        }
        if (this.process != null) {
            requestClear(); // a new cast supersedes any in-flight process (no cost)
        }
        submitTargets(spellId, snapshot, latestSnapshotSequence + 1);
        if (SpellCast.Mode.from(spell) == SpellCast.Mode.INSTANT) {
            fire(spellEntry, SpellCast.Action.RELEASE, 1F);
            return;
        }
        // Server-computed cast timing — the client's prediction is display-only from here
        var details = SpellParameters.getCastTimeDetails(player, spell);
        var process = new SpellCast.Process(player, spellEntry, itemStack.getItem(), details.speed(), details.length(), player.getWorld().getTime());
        ((SpellCasterEntity) player).setChannelTickIndex(0);
        SpellCastSyncHelper.setCasting(player, process);
        SoundHelper.playSound(player.getWorld(), player, spell.active.cast.start_sound);
    }

    /// [END-input] The player let go: cancels a timed cast (no cost), completes a channel early
    /// (settling its proportional cost), or releases a charge — with the final snapshot of the
    /// release frame (zero staleness). The client decides *whether* an input means ending
    /// (hold-vs-toggle policy); the server decides what ending *does*.
    public void requestEnd(Identifier spellId, SpellCast.TargetSnapshot snapshot) {
        submitTargets(spellId, snapshot, latestSnapshotSequence + 1);
        var process = this.process;
        if (process == null || !process.id().equals(spellId)) {
            return;
        }
        var spell = process.spell().value();
        var mode = SpellCast.Mode.from(spell);
        if (!SpellCast.serverAuthoritative(mode)) {
            return;
        }
        var ratio = process.progress(player.getWorld().getTime()).ratio();
        switch (mode) {
            case CASTING -> requestClear(); // cancelled before completion — nothing fires
            case CHANNEL -> {
                // Early completion: the RELEASE settles (proportional) cost, no impact
                fire(process.spell(), SpellCast.Action.RELEASE, ratio);
                if (this.process == process) {
                    requestClear();
                }
            }
            case CHARGED -> {
                var charge = spell.active.cast.charge;
                if (charge != null && ratio < charge.min_release_ratio) {
                    requestClear(); // fizzle — no cost, no fire
                    return;
                }
                fire(process.spell(), SpellCast.Action.RELEASE, ratio);
                if (this.process == process) {
                    requestClear();
                }
            }
            default -> requestClear();
        }
    }

    /// [TARGET-stream] Latest-wins replication of the client's cursor targeting. Validated
    /// against the option's wire contract, stored, consumed by server-initiated fires (Phase D).
    public void submitTargets(Identifier spellId, SpellCast.TargetSnapshot snapshot, int sequence) {
        var spellEntry = SpellRegistry.from(player.getWorld()).getEntry(spellId).orElse(null);
        if (spellEntry == null) {
            return;
        }
        var option = SpellCast.Option.of(spellEntry);
        if (!snapshot.conformsTo(option.targeting())) {
            return;
        }
        if (!spellId.equals(latestSnapshotSpellId)) {
            // New spell context: the previous stream's sequence numbering restarts
            latestSnapshotSequence = -1;
        }
        if (sequence <= latestSnapshotSequence) {
            return; // Late arrival, a newer snapshot is already in place
        }
        latestSnapshot = snapshot;
        latestSnapshotSpellId = spellId;
        latestSnapshotSequence = sequence;
    }

    @Nullable public SpellCast.TargetSnapshot latestSnapshot(Identifier spellId) {
        return spellId.equals(latestSnapshotSpellId) ? latestSnapshot : null;
    }
}
