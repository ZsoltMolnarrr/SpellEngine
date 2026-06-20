package net.spell_engine.api.spell.summon;

import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.fx.Sound;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/// Declarative definition of a spell-summoned entity.
///
/// Bundles everything needed to spawn and configure a summon: the entity type to spawn, its full
/// runtime {@link SummonBehaviour}, and where/how it is placed in the world (reusing SpellEngine's
/// {@link Spell.EntityPlacement}).
///
/// This mirrors SpellEngine's built-in {@link Spell.Impact.Action.Spawn} so it can later be lifted
/// into SpellEngine as a first-class impact data definition (e.g. `Spell.Impact.Action.Summon`).
/// Until SpellEngine's CUSTOM impact carries a JSON payload, instances live in the Wizards project
/// and are looked up by the custom-impact handler id.
///
/// Note: unlike {@link Spell.Impact.Action.Spawn}, time-to-live is not a separate field here — it is
/// part of {@link SummonBehaviour#lifespan}.
public class Summon {
    /// Registry id of the entity type to spawn. The entity type must implement {@link SpellSummoned}.
    public String entity_type_id;

    /// Full runtime behaviour: lifespan, movement, targeting, actions, sounds, attribute scaling.
    public SummonBehaviour behaviour = new SummonBehaviour();

    /// Per-entity spawn-location slots within a group, reusing SpellEngine's placement type.
    /// {@link #spawn_count} entities are spawned per group, cycling through this list in order and
    /// wrapping around (slot `i % placements.size()`).
    public List<Spell.EntityPlacement> placements = new ArrayList<>();

    /// How many entities to spawn per group. Each one takes the next per-entity placement slot,
    /// wrapping around the {@link #placements} list. Defaults to 1.
    public int spawn_count = 1;

    /// Group-level placement slots. {@link #group_count} groups are spawned; group `g` uses
    /// `group_placements.get(g % group_placements.size())` as a translation offset (its resulting
    /// position seeds the per-entity placements) applied to EVERY entity in that group. Empty (the
    /// default) means a single group anchored directly at the caster, with no group offset.
    ///
    /// Composition is translation only: a group offset moves where its formation is anchored, but the
    /// in-group formation always keeps the caster-relative orientation — a group cannot rotate its
    /// children relative to another group.
    public List<Spell.EntityPlacement> group_placements = new ArrayList<>();

    /// How many groups to spawn. Each group replays the full per-entity formation
    /// ({@link #spawn_count} / {@link #placements}), translated by the next group placement.
    /// Defaults to 1.
    public int group_count = 1;

    /// One-shot FX emitted server-side once per group, at the group's anchor position, deferred by
    /// the group placement's `delay_ticks`. Null = none.
    @Nullable public SummonFx group_spawn_fx = null;

    /// Sound played once per group when it spawns, at the group's anchor position (deferred by the
    /// group placement's `delay_ticks`). Null = none.
    @Nullable public Sound group_spawn_sound = null;

    public Summon() {}

    public Summon(String entity_type_id, SummonBehaviour behaviour, List<Spell.EntityPlacement> placements, int spawn_count) {
        this.entity_type_id = entity_type_id;
        this.behaviour = behaviour;
        this.placements = placements;
        this.spawn_count = spawn_count;
    }

    public Summon(String entity_type_id, SummonBehaviour behaviour,
                  List<Spell.EntityPlacement> placements, int spawn_count,
                  List<Spell.EntityPlacement> group_placements, int group_count) {
        this(entity_type_id, behaviour, placements, spawn_count);
        this.group_placements = group_placements;
        this.group_count = group_count;
    }
}
