package net.spell_engine.fabric;

import net.fabricmc.api.ModInitializer;
import net.spell_engine.SpellEngineMod;

public final class FabricMod implements ModInitializer {
    @Override
    public void onInitialize() {
        SpellEngineMod.init();
    }
}
