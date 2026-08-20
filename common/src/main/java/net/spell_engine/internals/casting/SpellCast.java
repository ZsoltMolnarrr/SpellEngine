package net.spell_engine.internals.casting;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.internals.container.SpellContainerSource;
import net.spell_engine.internals.cost.Ammo;
import net.spell_engine.internals.target.SpellTarget;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import net.spell_engine.internals.SpellParameters;

public class SpellCast {
    public record Attempt(Result result,
                          @Nullable MissingItemInfo missingItem,
                          @Nullable OnCooldownInfo onCooldown) {
        public enum Result { SUCCESS, MISSING_ITEM, ON_COOLDOWN, NONE }
        public record MissingItemInfo(Ammo.Searched item) { }
        public record OnCooldownInfo() { }

        public static Attempt none() {
            return new Attempt(Result.NONE, null, null);
        }

        public static Attempt success() {
            return new Attempt(Result.SUCCESS, null, null);
        }

        public static Attempt failMissingItem(MissingItemInfo missingItem) {
            return new Attempt(Result.MISSING_ITEM, missingItem, null);
        }

        public static Attempt failOnCooldown(OnCooldownInfo onCooldown) {
            return new Attempt(Result.ON_COOLDOWN, null, onCooldown);
        }

        public boolean isSuccess() {
            return result == Result.SUCCESS;
        }
        public boolean isFail() {
            return result != Result.SUCCESS && result != Result.NONE;
        }
    }

    public record Duration(float speed, int length) {
        public static final Duration EMPTY = new Duration(0, 0);
    }
    public static class TickHolder {
        public ArrayList<Float> ticks = new ArrayList<>();
    }
    public record Process(RegistryEntry<Spell> spell, Item item, float speed, int length, long startedAt, TickHolder tickHolder) {
        public Process(LivingEntity caster, RegistryEntry<Spell> spell, Item item, float speed, int length, long startedAt) {
            this(spell, item, speed, length, startedAt, new TickHolder());
            if (SpellParameters.isChanneled(spell.value())) {
                var channelCount = SpellParameters.channelTicks(caster, spell);
                var interval = channelInterval(caster);
                var offset = -interval * 0.5F;
                for (int i = 1; i <= channelCount; i++) {
                    tickHolder.ticks.add((interval * i) + offset);
                }
            }
        }

        public int spellCastTicksSoFar(long worldTime) {
            // At least zero
            // The difference must fit into an integer
            return (int)Math.max(worldTime - startedAt, 0);
        }

        public Progress progress(int castTicks) {
            if (length <= 0) {
                return new Progress(1F, this);
            }
            float ratio = Math.min(((float)castTicks) / length(), 1F);
            return new Progress(ratio, this);
        }

        public Progress progress(long worldTime) {
            int castTicks = spellCastTicksSoFar(worldTime);
            return progress(castTicks);
        }

        public Identifier id() {
            return spell.getKey().get().getValue();
        }

        // Both sync producers below are read at process start (the tracked data is written when
        // the process is set), so `tickHolder` still holds the full channel schedule and its size
        // is the declared cadence — the number of channel ticks this cast will perform.

        public SyncFormat sync() {
            return new SyncFormat(id().toString(), speed, length, tickHolder.ticks.size());
        }

        public String fastSyncJSON() {
            return "{\"i\":" + '"' + id().toString() + '"'  + ",\"s\":" + speed + ",\"l\":" + length + ",\"t\":" + tickHolder.ticks.size() + "}";
        }

        @Nullable
        public static Process fromSync(LivingEntity caster, World world, SyncFormat sync, Item item, long startedAt) {
            var spellId = sync.i();
            if (spellId.isEmpty()) {
                return null;
            }
            var id = Identifier.of(spellId);
            var spellEntry = SpellRegistry.from(world).getEntry(id).orElse(null);
            return new Process(caster, spellEntry, item, sync.s(), sync.l(), startedAt);
        }

        /**
         * Represents the spell cast process in a format that can be sent to the client.
         * Short field names are used to improve JSON performance.
         * `i` = spell id, `s` = casting speed, `l` = length in ticks,
         * `t` = declared channel cadence (number of channel ticks; 0 for non-channeled casts).
         */
        public record SyncFormat(String i, float s, int l, int t) { }

        public float channelInterval(LivingEntity caster) {
            var ticks = SpellParameters.channelTicks(caster, spell());
            if (ticks > 0) {
                return length / (float)ticks;
            } else {
                return length;
            }
        }

        public boolean isDue(long time) {
            var castTicks = spellCastTicksSoFar(time);
            if (tickHolder.ticks.isEmpty()) {
                return false;
            } else {
                return castTicks >= tickHolder.ticks.getFirst();
            }
        }
        public void markDue() {
            if (!tickHolder.ticks.isEmpty()) {
                tickHolder.ticks.removeFirst();
            }
        }
    }
    public record Progress(float ratio, Process process) { }

    public enum Mode {
        INSTANT,
        CASTING,
        CHANNEL,
        CHARGED,
        PASSIVE,
        ITEM_USE; // This one is never produced by mapping, only manually from SpellHotbar logic
        public static Mode from(Spell spell) {
            if (spell.active != null) {
                if (spell.active.cast.duration <= 0) {
                    return INSTANT;
                }
                if (SpellParameters.isChanneled(spell)) {
                    return CHANNEL;
                }
                if (spell.active.cast.type == Spell.Active.Cast.Type.CHARGE) {
                    return CHARGED;
                }
                return CASTING;
            } else {
                return PASSIVE;
            }
        }
    }


    /// An actively castable spell of a caster: the spell itself, the derived classification of
    /// its AUTHORED cast mechanic ({@link Mode}) and how it acquires targets
    /// ({@link Targeting}), plus the container-scoped resolved magnitudes flattened in
    /// (`range`, `channelTicks` — base values + spell modifier contributions). `range` is the
    /// MAXIMUM effective range: modifiers plus the full charge `range_add` — the client targets
    /// at this bound and the server filters to the true (ratio-scaled) range at fire. Everything
    /// is derived, never authored. The flattened values let clients predict targeting range and
    /// channel cadence WITHOUT knowing the caster's modifier spells; they refresh whenever the
    /// caster's containers change (the interactor re-derives and re-declares). Attribute-scoped
    /// magnitudes (haste-driven cast duration) are deliberately NOT flattened — attributes sync
    /// natively and change more often than containers. Passive and FROM_TRIGGER-driven spells
    /// are not options.
    ///
    /// **`mode` is classification, not a behavior promise.** Effect-scoped state can change what
    /// actually happens at cast time — most notably `InstantCast` effects, which make a CASTING
    /// spell fire instantly. Anything deciding actual cast behavior must consult the live,
    /// caster-aware checks (`SpellParameters.isInstantCast`, `getCastTimeDetails`) at the
    /// decision point; `mode` serves stable concerns only (input policy, wire contract, sorting).
    public record Option(RegistryEntry<Spell> spell, Mode mode, Targeting targeting,
                         float range, int channelTicks) {

        /// Caster-aware derivation: resolved values include the caster's spell modifiers;
        /// `range` is the maximum (full-charge) reach.
        public static Option of(PlayerEntity caster, RegistryEntry<Spell> spellEntry) {
            var spell = spellEntry.value();
            return new Option(spellEntry, Mode.from(spell), Targeting.of(spell),
                    SpellParameters.getMaxRange(caster, spellEntry),
                    SpellParameters.channelTicks(caster, spellEntry));
        }

        /// The actively castable spells of a player, in container order, with resolved values.
        /// (There is deliberately no caster-free derivation: call sites that only need
        /// classification use {@link Targeting#of} directly; resolved magnitudes always
        /// come from a caster.)
        public static List<Option> allOf(PlayerEntity player) {
            return SpellContainerSource.activeSpellsOf(player).stream()
                    .map(entry -> Option.of(player, entry))
                    .toList();
        }

        public Identifier id() {
            return spell.getKey().get().getValue();
        }

        /// Wire format for the options tracked data (see `PlayerEntityMixin`): spell id +
        /// the flattened resolved values; classification is re-derived from the spell on arrival.
        public record SyncFormat(String i, float r, int t) { }

        public SyncFormat sync() {
            return new SyncFormat(id().toString(), range, channelTicks);
        }

        @Nullable public static Option fromSync(World world, SyncFormat sync) {
            var entry = SpellRegistry.from(world).getEntry(Identifier.of(sync.i())).orElse(null);
            if (entry == null) {
                return null;
            }
            var spell = entry.value();
            return new Option(entry, Mode.from(spell), Targeting.of(spell), sync.r(), sync.t());
        }

        /// How a cast of this spell acquires its targets, and — for the cursor-driven shapes —
        /// what a client-submitted {@link TargetSnapshot} may contain (the wire contract).
        public record Targeting(Shape shape, int maxEntities, boolean entityRequired, boolean locationAllowed) {
            public enum Shape {
                NONE,           /// Server-resolved: no targets
                SELF,           /// Server-resolved: the caster
                AROUND_CASTER,  /// Server-resolved: caster-centered area lookup
                CURSOR_ENTITY,  /// Client-resolved: at most one aimed entity, or an aim location
                CURSOR_RAY      /// Client-resolved: ordered entities along the aim ray
            }

            /// Hard bound for CURSOR_RAY entity lists, applied when the spell declares no
            /// `target.cap` of its own.
            public static final int RAY_ENTITY_CAP = 32;

            /// Whether targets are resolved by the caster's client (cursor-driven shapes).
            public boolean clientResolved() {
                return shape == Shape.CURSOR_ENTITY || shape == Shape.CURSOR_RAY;
            }

            public static Targeting of(Spell spell) {
                var target = spell.target;
                return switch (target.type) {
                    // FROM_TRIGGER spells receive their targets from the trigger context; they
                    // are never actively cast, so they classify as needing no targeting input.
                    case NONE, FROM_TRIGGER -> new Targeting(Shape.NONE, 0, false, false);
                    case CASTER -> new Targeting(Shape.SELF, 0, false, false);
                    case AREA -> new Targeting(Shape.AROUND_CASTER, 0, false, false);
                    case AIM -> {
                        var required = target.aim != null && target.aim.required;
                        yield new Targeting(Shape.CURSOR_ENTITY, 1, required, !required);
                    }
                    case BEAM -> new Targeting(Shape.CURSOR_RAY,
                            target.cap > 0 ? Math.min(target.cap, RAY_ENTITY_CAP) : RAY_ENTITY_CAP,
                            false, false);
                };
            }
        }
    }

    /// A client-resolved targeting result, replicated to the server as state ("this is what my
    /// cursor selects right now"), never as an event. One structure serves all three submission
    /// positions: carried by an instant cast request, streamed while a timed cursor-driven cast
    /// is active, and carried by a charge release input.
    public record TargetSnapshot(List<Integer> entityIds, @Nullable Vec3d location) {
        public static final TargetSnapshot EMPTY = new TargetSnapshot(List.of(), null);

        public static TargetSnapshot of(SpellTarget.SearchResult result) {
            return new TargetSnapshot(result.entities().stream().map(Entity::getId).toList(), result.location());
        }

        /// Clamps this snapshot to an option's wire contract — never rejects, because a state
        /// update that gets dropped stalls the receiver on older state (e.g. a beam crossing
        /// more entities than the cap would freeze the server's targets). Ids past
        /// `maxEntities` are cut, a disallowed location is dropped, non-cursor shapes reduce
        /// to EMPTY. `entityRequired` is deliberately not involved: a missing required entity
        /// gates the fire, not the stream.
        public TargetSnapshot sanitizedFor(Option.Targeting targeting) {
            if (!targeting.clientResolved()) {
                return EMPTY;
            }
            var ids = entityIds.size() > targeting.maxEntities()
                    ? List.copyOf(entityIds.subList(0, targeting.maxEntities()))
                    : entityIds;
            var clampedLocation = targeting.locationAllowed() ? location : null;
            if (ids == entityIds && clampedLocation == location) {
                return this;
            }
            return new TargetSnapshot(ids, clampedLocation);
        }
    }

    public enum Action {
        CHANNEL,
        RELEASE,
        TRIGGER
    }

    public enum Animation {
        CASTING, RELEASE, MISC
    }
}
