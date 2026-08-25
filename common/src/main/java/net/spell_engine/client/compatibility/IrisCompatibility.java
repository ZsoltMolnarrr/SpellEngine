package net.spell_engine.client.compatibility;

import net.minecraft.client.render.RenderLayer;
import net.spell_engine.Platform;
import net.spell_engine.api.render.CustomLayers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Iris 1.10+ (1.21.11) maps every {@code RenderPipeline} to one of its shader programs and logs
 * "Missing program … in override list" on each draw of a pipeline it does not know. Custom pipelines must be
 * declared through {@code IrisApi.assignPipeline}; this does that for all of Spell Engine's (see
 * {@link CustomLayers#customPipelines()}). Iris is compile-only, so the API is only touched behind the mod check.
 */
public class IrisCompatibility {
    private static final Logger LOGGER = LoggerFactory.getLogger("SpellEngine/IrisCompat");
    private static boolean warned = false;

    /// Call once from client init
    public static void assignPipelines() {
        if (!Platform.util().isModLoaded("iris")) {
            return;
        }
        try {
            IrisPipelines.assign();
        } catch (Throwable e) {
            LOGGER.warn("Failed to register Spell Engine pipelines with Iris: {}", e.toString());
        }
    }

    /// Separate class so the Iris API classes are only loaded when Iris is present
    private static class IrisPipelines {
        static void assign() {
            var api = net.irisshaders.iris.api.v0.IrisApi.getInstance();
            for (var entry : CustomLayers.customPipelines().entrySet()) {
                var program = switch (entry.getValue()) {
                    case ENTITY_TRANSLUCENT -> net.irisshaders.iris.api.v0.IrisProgram.ENTITIES_TRANSLUCENT;
                    case ENTITY_EMISSIVE -> net.irisshaders.iris.api.v0.IrisProgram.EMISSIVE_ENTITIES;
                    case BEACON_BEAM -> net.irisshaders.iris.api.v0.IrisProgram.BEACON_BEAM;
                };
                api.assignPipeline(entry.getKey(), program);
            }
            LOGGER.info("Registered {} custom pipelines with Iris", CustomLayers.customPipelines().size());
        }
    }

    /// Iris 1.10 removed the decal (`BlendingStateHolder`) API the item glow used to be ordered with;
    /// the glow pipeline is now declared as an emissive-entity program instead (see `assignPipelines`).
    public static void markAsDecal(RenderLayer layer) {
        if (!warned) {
            warned = true;
            LOGGER.debug("Iris decal marking is not available on this Iris version; glow uses the emissive entity program");
        }
    }
}
