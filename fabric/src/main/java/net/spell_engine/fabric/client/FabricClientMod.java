package net.spell_engine.fabric.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.client.input.Keybindings;
import net.spell_engine.client.render.CustomModelRegistry;

public final class FabricClientMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        SpellEngineClient.init();
        SpellEngineClient.registerParticleAppearances();
        registerKeyBindings();
        registerModels();
        ModelLoadingPlugin.register(new FabricModelDiscovery());
    }

    private static void registerKeyBindings() {
        for(var keybinding: Keybindings.all()) {
            KeyBindingHelper.registerKeyBinding(keybinding);
        }
    }

    private static void registerModels() {
        ModelLoadingPlugin.register(pluginCtx -> {
            pluginCtx.addModels(CustomModelRegistry.getModelIds());
        });
    }
}
