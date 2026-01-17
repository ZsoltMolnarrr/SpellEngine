package net.spell_engine.fabric.client;

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.MinecraftClient;
import net.spell_engine.client.render.ScrollModelDiscovery;

/**
 * Fabric-specific implementation for spell scroll model discovery and registration.
 * Discovers models via {@link ScrollModelDiscovery} and registers them for Fabric's model system.
 */
public class FabricModelDiscovery implements ModelLoadingPlugin {
    @Override
    public void onInitializeModelLoader(Context pluginContext) {
        var resourceManager = MinecraftClient.getInstance().getResourceManager();
        var models = ScrollModelDiscovery.discoverScrollModels(resourceManager);
        pluginContext.addModels(models);
    }
}
