package net.spell_engine.fabric;

import net.fabricmc.api.ModInitializer;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.effect.SpellEngineEffects;
import net.spell_engine.fabric.compat.FabricCompatFeatures;
import net.spell_engine.fx.SpellEngineParticles;
import net.spell_engine.fx.SpellEngineSounds;
import net.spell_engine.item.SpellEngineItems;

public final class FabricMod implements ModInitializer {
    @Override
    public void onInitialize() {
        SpellEngineMod.registerEntityTypes();
        SpellEngineSounds.register();
        SpellEngineParticles.register();
        SpellEngineEffects.register();
        SpellEngineItems.register();
        SpellEngineMod.registerCriteria();
        SpellEngineMod.registerSpellBinding();

        SpellEngineMod.init();

        FabricCompatFeatures.initialize();
    }
}
