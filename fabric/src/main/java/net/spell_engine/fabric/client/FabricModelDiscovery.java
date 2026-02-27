package net.spell_engine.fabric.client;

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.MinecraftClient;
import net.spell_engine.client.render.CustomModelDiscovery;

/**
 * Fabric-specific implementation for spell model discovery and registration.
 * Discovers models via {@link CustomModelDiscovery} and registers them for Fabric's model system.
 * Handles scrolls, books, projectiles, and effects.
 */
public class FabricModelDiscovery implements ModelLoadingPlugin {
    @Override
    public void onInitializeModelLoader(Context pluginContext) {
        var resourceManager = MinecraftClient.getInstance().getResourceManager();
        var models = CustomModelDiscovery.discoverScrollModels(resourceManager);
        pluginContext.addModels(models);
    }
}
