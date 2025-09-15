package net.spell_engine.client.beam;

import net.spell_engine.internals.delivery.Beam;

public interface BeamEmitterEntity {
    void setLastRenderedBeam(Beam.Rendered beam);
}