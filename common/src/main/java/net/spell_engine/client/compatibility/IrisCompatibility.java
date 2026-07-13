package net.spell_engine.client.compatibility;

import net.irisshaders.batchedentityrendering.impl.BlendingStateHolder;
import net.irisshaders.batchedentityrendering.impl.TransparencyType;
import net.minecraft.client.render.RenderLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Iris batches by its own rules, and sorts what it batches into transparency buckets. It reads the bucket
 * off a layer's transparency phase <i>by identity</i>: `NO_TRANSPARENCY` is opaque, `GLINT_TRANSPARENCY`
 * and `CRUMBLING_TRANSPARENCY` are decals, and anything else falls through to generally transparent.
 * Decals are drawn last, after the geometry they sit on, which is how the vanilla glint comes to lie on
 * top of its item.
 * <p>
 * Our glow blends additively, through a transparency of its own, so Iris cannot recognize it and files it
 * as ordinary transparent geometry - the same bucket as the item, with nothing to say it comes after.
 * Drawn first, it depth tests against a buffer the item has not written yet, and since it tests for
 * `EQUAL` (see `CustomLayers.ITEM_GLOW_DEPTH_TEST`), every fragment is rejected and the glow vanishes
 * without a word. So we tell Iris what it cannot infer: this is a decal, like the glint beside it.
 * <p>
 * Nothing here may be touched unless Iris is loaded, or the class fails to resolve. Guard every call with
 * {@link ShaderCompatibility#isVanillaRenderSystem()}.
 */
public class IrisCompatibility {
    private static final Logger LOGGER = LoggerFactory.getLogger("SpellEngine/IrisCompat");

    public static void markAsDecal(RenderLayer layer) {
        try {
            if (layer instanceof BlendingStateHolder holder) {
                holder.setTransparencyType(TransparencyType.DECAL);
            }
        } catch (Throwable throwable) {
            // Iris internals, not its API, so a version bump may move this. Losing the glow under a
            // shader pack is a fair price for not taking the game down with it.
            LOGGER.warn("Could not mark {} as a decal for Iris, it may not render under a shader pack",
                    layer, throwable);
        }
    }
}
