package net.spell_engine.fabric.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.client.input.Keybindings;

public final class FabricClientMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        SpellEngineClient.init();
        SpellEngineClient.registerParticleAppearances();
        registerKeyBindings();
    }

    public static void registerKeyBindings() {
        for(var keybinding: Keybindings.all()) {
            KeyBindingHelper.registerKeyBinding(keybinding);
        }
    }
}
