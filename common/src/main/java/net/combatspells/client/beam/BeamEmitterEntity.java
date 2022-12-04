package net.combatspells.client.beam;

import net.combatspells.internals.Beam;

public interface BeamEmitterEntity {
    void setLastRenderedBeam(Beam.Rendered beam);
}