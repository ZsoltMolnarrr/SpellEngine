package net.spell_engine.neoforge;

import net.neoforged.fml.common.Mod;

import net.spell_engine.ExampleMod;

@Mod(ExampleMod.MOD_ID)
public final class ExampleModNeoForge {
    public ExampleModNeoForge() {
        // Run our common setup.
        ExampleMod.init();
    }
}
