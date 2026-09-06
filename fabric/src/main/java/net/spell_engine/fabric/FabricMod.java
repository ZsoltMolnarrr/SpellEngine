package net.spell_engine.fabric;

import net.fabricmc.api.ModInitializer;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.fabric.compat.FabricCompatFeatures;
import net.spell_engine.fabric.network.FabricServerNetwork;
import net.spell_engine.item.SpellEngineItems;

public final class FabricMod implements ModInitializer {
    @Override
    public void onInitialize() {
        // Attributes are normally already registered by the Fabric-only `EntityAttributes` <clinit> mixin; idempotent.
        SpellEngineMod.registerAttributes();
        SpellEngineMod.registerEntityTypes();
        SpellEngineMod.registerSounds();
        SpellEngineMod.registerParticles();
        SpellEngineMod.registerStatusEffects();
        SpellEngineItems.register();
        SpellEngineMod.registerCriteria();
        SpellEngineMod.registerSpellBinding();
        SpellEngineMod.registerEnchantments();

        // Networking first: common init registers the play-phase handlers / may queue packets through Platform.util().
        FabricServerNetwork.init();
        SpellEngineMod.init();

        FabricCompatFeatures.initialize();
    }
}
