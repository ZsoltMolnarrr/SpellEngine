package net.spell_engine.client.render.tint;

import net.minecraft.client.render.entity.state.EntityRenderState;
import net.spell_engine.api.effect.EntityTints;
import net.spell_engine.client.render.extension.EntityRenderStateExtension;
import org.jetbrains.annotations.Nullable;

/// Render-thread scope: the render state whose *extra* passes are being submitted — vanilla features
/// (armor, cape, elytra, held/head items, shoulder parrots…), modded armor and Spell Engine's custom
/// status effect models. The tint itself lives on the state ([EntityRenderStateExtension]); this scope
/// only names which state's tint applies to submissions that carry no usable state of their own.
/// <p>
/// Why a scope is still needed at all: `LivingEntityRenderer.render` submits the body with the state's
/// tint mixed in natively (`getMixColor`), but feature passes issue `submitModel`/`submitModelPart` calls
/// whose `tintedColor` is never derived from the entity: `submitModelPart` takes no state, and features
/// that call `submitModel` may pass a sub-state (a shoulder parrot's) that was never tinted, while
/// `RenderLayers.armorCutoutNoCull` is a static factory with no entity context at all.
/// So `BatchingRenderCommandQueueTintMixin` multiplies the scoped state's tint into those calls, and
/// `RenderLayersMixin` swaps the armor layer for a translucent one while the scoped state is translucent.
/// The scope opens after the body submission and closes at the end of `LivingEntityRenderer.render`
/// (both in `LivingEntityRendererMixin`), so the body is never tinted twice.
public final class EntityTintPass {
    @Nullable private static EntityRenderStateExtension state;

    public static void begin(EntityRenderState renderState) {
        var extension = (EntityRenderStateExtension) renderState;
        state = extension.spellEngine_getTint() == EntityTints.NEUTRAL ? null : extension;
    }

    public static void end() {
        state = null;
    }

    /// True when the scoped state's tint has alpha below 1
    public static boolean isTranslucent() {
        return state != null && state.spellEngine_hasTranslucentTint();
    }

    /// `color` multiplied by the scoped state's tint, or unchanged outside a tinted pass
    public static int apply(int color) {
        return state == null ? color : EntityTints.multiply(color, state.spellEngine_getTint());
    }
}
