package net.spell_engine.api.spell.summon;

import net.spell_engine.api.spell.fx.ModelEffect;
import net.spell_engine.api.spell.fx.ParticleBatch;

import java.util.List;

/// A one-shot visual FX bundle: particle batches and model effects, emitted server-side at a single
/// moment (e.g. when a summon, or a group of summons, spawns). Mirrors the visual half of SpellEngine's
/// {@link net.spell_engine.api.spell.Spell.Delivery.Cloud.Spawn}.
///
/// Sound is intentionally not part of this structure — summons carry their own lifecycle sounds via
/// {@link SummonBehaviour.Sounds} (spawn/despawn/ambient/…).
///
/// One-shot FX are emitted from the server (particles via a tracker packet, models as self-syncing
/// entities) — unlike continuous existence particles, which are spawned client-side to avoid
/// per-tick traffic.
public class SummonFx {
    public ParticleBatch[] particles = new ParticleBatch[]{};
    public List<ModelEffect> model_fx = List.of();
}
