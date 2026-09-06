package net.spell_engine.forge.client;

import net.minecraft.client.MinecraftClient;
import net.minecraftforge.client.event.ModelEvent;
import net.spell_engine.client.render.CustomModelDiscovery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Forge-specific implementation for spell model discovery and registration.
 * Discovers models via {@link CustomModelDiscovery} and registers them for Forge's model system.
 * Handles scrolls, books, projectiles, and effects.
 */
public class ForgeModelDiscovery {
    private static final Logger LOGGER = LoggerFactory.getLogger("SpellEngine/ForgeModelDiscovery");

    /**
     * Discovers and registers spell models for Forge.
     * Called from {@link ModelEvent.RegisterAdditional} event handler.
     * Scans multiple model directories: spell_scroll, spell_book, spell_projectile, spell_effect.
     *
     * @param event The model registration event
     */
    public static void registerCustomModels(ModelEvent.RegisterAdditional event) {
        try {
            var resourceManager = MinecraftClient.getInstance().getResourceManager();
            var discoveredModels = CustomModelDiscovery.discoverScrollModels(resourceManager);

            // 1.20.1 / Forge 47: additional models are registered by plain Identifier (no `ModelIdentifier.standalone`)
            // and looked up by the same Identifier from `BakedModelManager.models` (see BakedModelManagerAccessor).
            for (var modelId : discoveredModels) {
                event.register(modelId);
                LOGGER.debug("Registered spell model for Forge: {}", modelId);
            }

            if (!discoveredModels.isEmpty()) {
                LOGGER.info("Registered {} spell models for Forge", discoveredModels.size());
            }
        } catch (Exception e) {
            LOGGER.error("Error registering spell models for Forge", e);
        }
    }
}
