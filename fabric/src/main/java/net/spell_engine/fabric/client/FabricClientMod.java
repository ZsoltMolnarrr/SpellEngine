package net.spell_engine.fabric.client;

import net.fabricmc.api.ClientModInitializer;
import net.spell_engine.client.SpellEngineClient;

public final class FabricClientMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        SpellEngineClient.initialize();
    }
}
