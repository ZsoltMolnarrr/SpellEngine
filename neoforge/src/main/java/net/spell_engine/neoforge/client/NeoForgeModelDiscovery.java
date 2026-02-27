package net.spell_engine.neoforge.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ModelIdentifier;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.spell_engine.client.render.CustomModelDiscovery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NeoForge-specific implementation for spell model discovery and registration.
 * Discovers models via {@link CustomModelDiscovery} and registers them for NeoForge's model system.
 * Handles scrolls, books, projectiles, and effects.
 */
public class NeoForgeModelDiscovery {
    private static final Logger LOGGER = LoggerFactory.getLogger("SpellEngine/NeoForgeModelDiscovery");

    /**
     * Discovers and registers spell models for NeoForge.
     * Called from {@link ModelEvent.RegisterAdditional} event handler.
     * Scans multiple model directories: spell_scroll, spell_book, spell_projectile, spell_effect.
     *
     * @param event The model registration event
     */
    public static void registerCustomModels(ModelEvent.RegisterAdditional event) {
        try {
            var resourceManager = MinecraftClient.getInstance().getResourceManager();
            var discoveredModels = CustomModelDiscovery.discoverScrollModels(resourceManager);

            // Register each discovered model with NeoForge-specific wrapping
            for (var modelId : discoveredModels) {
                // NeoForge requires ModelIdentifier.standalone() wrapper for dynamically registered models
                var wrappedModelId = ModelIdentifier.standalone(modelId);
                event.register(wrappedModelId);
                LOGGER.debug("Registered spell model for NeoForge: {}", wrappedModelId);
            }

            if (!discoveredModels.isEmpty()) {
                LOGGER.info("Registered {} spell models for NeoForge", discoveredModels.size());
            }
        } catch (Exception e) {
            LOGGER.error("Error registering spell models for NeoForge", e);
        }
    }
}
