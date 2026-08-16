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
/// The timeline is fully server-authoritative: {@link #requestCast} starts (or, for instants,
/// immediately fires) a cast with server-computed timing, {@link #tick} drives completion and
/// channel cadence, {@link #requestEnd} handles the player's end-input, and targets come from
/// the client's streamed cursor snapshot ({@link #submitTargets}) or a server-side lookup.
public class SpellCastInteractor {

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

    /// [CAST-clear] Ends the casting process without firing, and resets the channel counter.
    public void requestClear() {
        setProcess(null);
        ((SpellCasterEntity) player).setChannelTickIndex(0);
    }

    // MARK: Server-authoritative timeline

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

    /// Server tick — the cast timeline: cancellation conditions, completion fire (CASTING),
    /// channel cadence (CHANNEL). CHARGE holds until its end-input.
    public void tick() {
        var process = this.process;
        if (process == null) {
            return;
        }
        var spell = process.spell().value();
        var mode = SpellCast.Mode.from(spell);
        var time = player.getWorld().getTime();

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

    // MARK: Signals — cast protocol

    /// The latest client-submitted targeting state ("what my cursor selects right now"), kept as
    /// a single slot. Client→server payloads ride one ordered channel, so the last received IS
    /// the newest — no sequence numbering needed. Consumed by server-initiated fires.
    @Nullable private SpellCast.TargetSnapshot latestSnapshot = null;
    @Nullable private Identifier latestSnapshotSpellId = null;

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
        submitTargets(spellId, snapshot);
        if (SpellCast.Mode.from(spell) == SpellCast.Mode.INSTANT) {
            fire(spellEntry, SpellCast.Action.RELEASE, 1F);
            return;
        }
        // Server-computed cast timing — the client's prediction is display-only from here
        var details = SpellParameters.getCastTimeDetails(player, spell);
        var process = new SpellCast.Process(player, spellEntry, itemStack.getItem(), details.speed(), details.length(), player.getWorld().getTime());
        ((SpellCasterEntity) player).setChannelTickIndex(0);
        setProcess(process);
        SoundHelper.playSound(player.getWorld(), player, spell.active.cast.start_sound);
    }

    /// [END-input] The player let go: cancels a timed cast (no cost), completes a channel early
    /// (settling its proportional cost), or releases a charge — with the final snapshot of the
    /// release frame (zero staleness). The client decides *whether* an input means ending
    /// (hold-vs-toggle policy); the server decides what ending *does*.
    public void requestEnd(Identifier spellId, SpellCast.TargetSnapshot snapshot) {
        submitTargets(spellId, snapshot);
        var process = this.process;
        if (process == null || !process.id().equals(spellId)) {
            return;
        }
        var spell = process.spell().value();
        var mode = SpellCast.Mode.from(spell);
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

    /// [TARGET-stream] Replication of the client's cursor targeting. Clamped to the option's
    /// wire contract and stored; arrival order is delivery order (ordered channel), so a plain
    /// overwrite keeps the slot newest.
    public void submitTargets(Identifier spellId, SpellCast.TargetSnapshot snapshot) {
        var spellEntry = SpellRegistry.from(player.getWorld()).getEntry(spellId).orElse(null);
        if (spellEntry == null) {
            return;
        }
        var option = SpellCast.Option.of(spellEntry);
        latestSnapshot = snapshot.sanitizedFor(option.targeting());
        latestSnapshotSpellId = spellId;
    }

    @Nullable public SpellCast.TargetSnapshot latestSnapshot(Identifier spellId) {
        return spellId.equals(latestSnapshotSpellId) ? latestSnapshot : null;
    }
}
