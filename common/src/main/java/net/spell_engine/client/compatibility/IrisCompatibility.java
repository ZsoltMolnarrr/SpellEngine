package net.spell_engine.client.compatibility;

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
            // Per-entry try: `assignPipeline` throws if a pipeline is assigned twice, and a single throw here
            // used to abort every REMAINING assignment silently (the catch sits outside this loop). The beam
            // pipelines are early in the map, so a later failure must not cost them their shader program.
            for (var entry : CustomLayers.customPipelines().entrySet()) {
                var program = switch (entry.getValue()) {
                    case ENTITY_TRANSLUCENT -> net.irisshaders.iris.api.v0.IrisProgram.ENTITIES_TRANSLUCENT;
                    case ENTITY_EMISSIVE -> net.irisshaders.iris.api.v0.IrisProgram.EMISSIVE_ENTITIES;
                    case BEACON_BEAM -> net.irisshaders.iris.api.v0.IrisProgram.BEACON_BEAM;
                    case GLINT -> net.irisshaders.iris.api.v0.IrisProgram.ARMOR_GLINT;
                };
                try {
                    api.assignPipeline(entry.getKey(), program);
                } catch (Throwable e) {
                    LOGGER.warn("Failed to assign pipeline {} to Iris: {}", entry.getKey().getLocation(), e.toString());
                }
            }
            LOGGER.info("Registered {} custom pipelines with Iris", CustomLayers.customPipelines().size());
            assignShadows(api);
        }

        /// Iris 1.11.6+ (26.3) keeps a second override list for its shadow pass; a pipeline missing from it logs
        /// "Missing program … in override list" with a stack trace on every shadow-pass draw. Iris maps the vanilla
        /// pipelines ours derive from (`ENTITY_TRANSLUCENT`, `ARMOR_CUTOUT_NO_CULL`, `ENTITY_TRANSLUCENT_EMISSIVE`,
        /// `BEACON_BEAM_*`, `GLINT`) to its entity / block shadow programs, so the same coarse split is used here.
        /// Older Iris (the NeoForge 26.2 jar) has no `assignPipelineShadow`; the first `NoSuchMethodError` ends the loop.
        private static void assignShadows(net.irisshaders.iris.api.v0.IrisApi api) {
            int assigned = 0;
            for (var entry : CustomLayers.customPipelines().entrySet()) {
                var program = switch (entry.getValue()) {
                    case ENTITY_TRANSLUCENT, ENTITY_EMISSIVE, GLINT -> net.irisshaders.iris.api.v0.IrisShadowProgram.SHADOW_ENTITIES;
                    case BEACON_BEAM -> net.irisshaders.iris.api.v0.IrisShadowProgram.SHADOW_BLOCK;
                };
                try {
                    api.assignPipelineShadow(entry.getKey(), program);
                    assigned++;
                } catch (NoSuchMethodError | NoClassDefFoundError e) {
                    LOGGER.info("Iris shadow pipeline assignment unavailable ({}), skipping", e.toString());
                    return;
                } catch (Throwable e) {
                    LOGGER.warn("Failed to assign shadow pipeline {} to Iris: {}", entry.getKey().getLocation(), e.toString());
                }
            }
            LOGGER.info("Registered {} custom pipelines with Iris for the shadow pass", assigned);
        }
    }
}
