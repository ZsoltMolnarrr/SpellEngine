package net.spell_engine.fabric.client;

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.item.ItemModels;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class FabricModelDiscovery implements ModelLoadingPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("SpellEngine/ScrollModelDiscovery");
    private static final String SCROLL_MODEL_PATH = "models/item/spell_scroll";

    @Override
    public void onInitializeModelLoader(Context pluginContext) {
        var resourceManager = MinecraftClient.getInstance().getResourceManager();
        var models = discoverScrollModels(resourceManager);
        pluginContext.addModels(models);
        pluginContext.modifyModelAfterBake().register((model, context) -> {
            if (context.resourceId() != null && context.resourceId().getPath().contains("spell_scroll")) {
                System.out.println("After bake modification for model: " + context.resourceId() + ", resource: " + context.topLevelId());
            }
            return model;
        });
    }

    /**
     * Discovers spell scroll models across all loaded mods.
     * Scans for files in: assets/{namespace}/models/item/spell_scrolls/**\/*.json
     *
     * @param resourceManager The resource manager to scan
     * @return List of discovered model Identifiers
     */
    private static List<Identifier> discoverScrollModels(ResourceManager resourceManager) {
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
