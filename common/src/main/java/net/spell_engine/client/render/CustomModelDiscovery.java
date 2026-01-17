package net.spell_engine.client.render;

import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Platform-agnostic utility for discovering spell-related models across all loaded mods.
 * Scans for JSON files in multiple model directories including scrolls, books, projectiles, and effects.
 */
public class CustomModelDiscovery {
    private static final Logger LOGGER = LoggerFactory.getLogger("SpellEngine/ModelDiscovery");

    private static final String MODEL_FOLDER = "models";
    // Model paths to scan for automatic discovery
    private static final String[] MODEL_PATHS = {
        "/item/spell_book",
        "/item/spell_scroll",
        "/spell_projectile",
        "/spell_effect"
    };

    /**
     * Discovers spell-related models across all loaded mods.
     * Scans for files in multiple model directories.
     *
     * @param resourceManager The resource manager to scan
     * @return List of discovered model Identifiers (without platform-specific wrapping)
     */
    public static List<Identifier> discoverScrollModels(ResourceManager resourceManager) {
        List<Identifier> discoveredModels = new ArrayList<>();

        try {
            // Find all resources matching the pattern
            var resources = resourceManager.findResources(
                    MODEL_FOLDER,
                    id -> {
                        var path = id.getPath();
                        String subPath = path.substring(MODEL_FOLDER.length());
                        for (String modelPath : MODEL_PATHS) {
                            if (subPath.startsWith(modelPath) && subPath.endsWith(".json")) {
                                return true;
                            }
                        }
                        return false;
                    }
            );

            int pathCount = 0;
            for (var entry : resources.entrySet()) {
                Identifier resourceId = entry.getKey();

                // Convert resource path to model identifier
                Identifier modelId = extractModelIdentifier(resourceId);

                if (modelId != null) {
                    discoveredModels.add(modelId);
                    LOGGER.debug("Discovered spell model: {}", modelId);
                    pathCount++;
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error scanning for spell models in {}", e);
        }
        return discoveredModels;
    }

    /**
     * Converts a resource Identifier to a model Identifier.
     * Examples:
     *   Input: "wizards:models/item/spell_scroll/arcane_scroll.json"
     *   Output: "wizards:item/spell_scroll/arcane_scroll"
     *
     *   Input: "spell_engine:models/spell_projectile/fireball.json"
     *   Output: "spell_engine:spell_projectile/fireball"
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
