package net.spell_engine.client.compatibility;

import net.minecraft.client.render.RenderLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Iris used to expose `BlendingStateHolder`/`TransparencyType` (batchedentityrendering impl) which let us mark
 * the additive item glow layer as a decal so it was drawn after the item it sits on. Iris 1.10 (1.21.11) removed
 * that package; the glow may be lost under shader packs until this is re-expressed via Iris' pipeline API.
 * TODO(post-migration): use `IrisApi`/`assignPipeline` for the glow layer.
 */
public class IrisCompatibility {
    private static final Logger LOGGER = LoggerFactory.getLogger("SpellEngine/IrisCompat");
    private static boolean warned = false;

    public static void markAsDecal(RenderLayer layer) {
        if (!warned) {
            warned = true;
            LOGGER.info("Iris decal marking is not available on this Iris version; item glow may not render under shader packs");
        }
    }
}
