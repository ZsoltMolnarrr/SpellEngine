package net.spell_engine.client.render;

import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Platform-agnostic utility for discovering spell scroll models across all loaded mods.
 * Scans for JSON files in: assets/{namespace}/models/item/spell_scroll/
 */
public class ScrollModelDiscovery {
    private static final Logger LOGGER = LoggerFactory.getLogger("SpellEngine/ScrollModelDiscovery");
    private static final String SCROLL_MODEL_PATH = "models/item/spell_scroll";

    /**
     * Discovers spell scroll models across all loaded mods.
     * Scans for files in: assets/{namespace}/models/item/spell_scrolls/
     *
     * @param resourceManager The resource manager to scan
     * @return List of discovered model Identifiers (without platform-specific wrapping)
     */
    public static List<Identifier> discoverScrollModels(ResourceManager resourceManager) {
        List<Identifier> discoveredModels = new ArrayList<>();

        try {
            // Find all resources matching the spell_scrolls pattern
            var resources = resourceManager.findResources(
                SCROLL_MODEL_PATH,
                path -> path.getPath().endsWith(".json")
            );

            for (var entry : resources.entrySet()) {
                Identifier resourceId = entry.getKey();

                // Convert resource path to model identifier
                Identifier modelId = extractModelIdentifier(resourceId);

                if (modelId != null) {
                    discoveredModels.add(modelId);
                    LOGGER.debug("Discovered spell scroll model: {}", modelId);
                }
            }

            LOGGER.info("Discovered {} spell scroll models", discoveredModels.size());
        } catch (Exception e) {
            LOGGER.error("Error scanning for spell scroll models", e);
        }

        return discoveredModels;
    }

    /**
     * Converts a resource Identifier to a model Identifier.
     * Input: "wizards:models/item/spell_scrolls/arcane_scroll.json"
     * Output: "wizards:item/spell_scrolls/arcane_scroll"
     *
     * @param resourceId The resource identifier
     * @return The model identifier, or null if the resource path is invalid
     */
    private static Identifier extractModelIdentifier(Identifier resourceId) {
        String path = resourceId.getPath();

        // Validate path starts with "models/"
        if (!path.startsWith("models/")) {
            LOGGER.warn("Invalid resource path (missing 'models/' prefix): {}", resourceId);
            return null;
        }

        // Strip "models/" prefix
        path = path.substring("models/".length());

        // Validate and strip ".json" suffix
        if (!path.endsWith(".json")) {
            LOGGER.warn("Invalid resource path (missing '.json' suffix): {}", resourceId);
            return null;
        }
        path = path.substring(0, path.length() - ".json".length());

        return Identifier.of(resourceId.getNamespace(), path);
    }
}
