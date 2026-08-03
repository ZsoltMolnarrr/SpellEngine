package net.spell_engine.api.spell.fx;

import java.util.List;

/// A one-shot visual FX bundle: particle batches and model effects, emitted server-side at a single
/// moment (e.g. when a summon, or a group of summons, spawns). The visual counterpart to a one-shot
/// {@link Sound}.
///
/// One-shot FX are emitted from the server — particles via a tracker packet, models as self-syncing
/// entities — unlike continuous effects, which are typically spawned client-side to avoid per-tick
/// network traffic.
public class VFX {
    public List<ParticleGroupEffect> particles = List.of();
    public List<ModelEffect> model_fx = List.of();
}
