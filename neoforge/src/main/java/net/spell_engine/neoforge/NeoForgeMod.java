package net.spell_engine.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

import net.spell_engine.SpellEngineMod;

@Mod(SpellEngineMod.ID)
public final class NeoForgeMod {
    public NeoForgeMod(IEventBus modBus) {
        // Run our common setup.
        SpellEngineMod.init();
    }
}
