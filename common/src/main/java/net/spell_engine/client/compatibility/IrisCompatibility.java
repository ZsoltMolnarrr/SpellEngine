package net.spell_engine.client.compatibility;

import net.minecraft.client.render.RenderLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

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
 * The bucket lives on `BlendingStateHolder`, which Iris mixes into `RenderLayer`. That is Iris
 * <i>internals</i>, not its API, and the package it sits in has moved: Iris 1.6.x (and Oculus on Forge)
 * ship it as `net.coderbot.batchedentityrendering.impl`, Iris 1.7+ as
 * `net.irisshaders.batchedentityrendering.impl`, identical in shape either way. No single import can
 * satisfy both, so the lookup below is reflective and tries both packages, newest first.
 * <p>
 * Nothing here may be touched unless Iris is loaded. Guard every call with
 * {@link ShaderCompatibility#isVanillaRenderSystem()}.
 */
public class IrisCompatibility {
    private static final Logger LOGGER = LoggerFactory.getLogger("SpellEngine/IrisCompat");

    /// Every package Iris has shipped `batchedentityrendering` under, newest first.
    private static final String[] CANDIDATE_PACKAGES = {
            "net.irisshaders.batchedentityrendering.impl", // Iris 1.7+
            "net.coderbot.batchedentityrendering.impl"     // Iris 1.6.x, Oculus on Forge
    };

    public static void markAsDecal(RenderLayer layer) {
        var decal = Decal.RESOLVED;
        if (decal == null) {
            // Absent, and already said so once at resolution time. Layers keep being created; stay quiet.
            return;
        }
        decal.applyTo(layer);
    }

    private static final class Decal {
        /// Resolved on first use and cached for the session - a failed resolution included, as `null`, so
        /// a package Iris no longer ships is looked up, and complained about, exactly once rather than
        /// once per layer. Class initialization gives us that once-only guarantee for free, and safely,
        /// however many threads ask at once.
        private static final Decal RESOLVED = resolve();

        private final Class<?> blendingStateHolder;
        private final Method setTransparencyType;
        private final Object decalTransparencyType;
        private volatile boolean failureReported = false;

        private Decal(Class<?> blendingStateHolder, Method setTransparencyType, Object decalTransparencyType) {
            this.blendingStateHolder = blendingStateHolder;
            this.setTransparencyType = setTransparencyType;
            this.decalTransparencyType = decalTransparencyType;
        }

        private static Decal resolve() {
            for (var candidate : CANDIDATE_PACKAGES) {
                try {
                    var holder = Class.forName(candidate + ".BlendingStateHolder");
                    var transparencyType = Class.forName(candidate + ".TransparencyType");
                    var setter = holder.getMethod("setTransparencyType", transparencyType);
                    var decal = transparencyType.getField("DECAL").get(null);
                    LOGGER.debug("Iris transparency buckets found in `{}`", candidate);
                    return new Decal(holder, setter, decal);
                } catch (Throwable ignored) {
                    // Wrong package for this Iris version, or a shape we no longer recognize. Try the next.
                }
            }
            LOGGER.warn("Iris is loaded, but `BlendingStateHolder`/`TransparencyType` were found in none of "
                            + "[{}]. Glowing items may not render under a shader pack.",
                    String.join(", ", CANDIDATE_PACKAGES));
            return null;
        }

        private void applyTo(RenderLayer layer) {
            try {
                if (blendingStateHolder.isInstance(layer)) {
                    setTransparencyType.invoke(layer, decalTransparencyType);
                }
            } catch (Throwable throwable) {
                // Losing the glow under a shader pack is a fair price for not taking the game down with
                // it. Once per session, not once per layer.
                if (!failureReported) {
                    failureReported = true;
                    LOGGER.warn("Could not mark {} as a decal for Iris, glowing items may not render under "
                            + "a shader pack", layer, throwable);
                }
            }
        }
    }
}
