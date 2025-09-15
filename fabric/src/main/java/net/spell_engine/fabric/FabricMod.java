package net.spell_engine.fabric;

import net.fabricmc.api.ModInitializer;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.fabric.compat.FabricCompatFeatures;

public final class FabricMod implements ModInitializer {
    @Override
    public void onInitialize() {
        SpellEngineMod.init();
        FabricCompatFeatures.initialize();
    }
}
