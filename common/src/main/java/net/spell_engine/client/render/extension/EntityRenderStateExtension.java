package net.spell_engine.client.render.extension;

import net.minecraft.entity.Entity;
import net.spell_engine.api.effect.EntityTints;
import org.jetbrains.annotations.Nullable;

/// Duck interface on `EntityRenderState`: the entity the state was extracted from and the tick delta,
/// for Spell Engine renderers/mixins that still need live entity data at render time
public interface EntityRenderStateExtension {
    void spellEngine_setEntity(@Nullable Entity entity, float tickDelta);
    @Nullable Entity spellEngine_getEntity();
    float spellEngine_getTickDelta();

    /// The entity tint (see [EntityTints]) extracted for this state, `EntityTints.NEUTRAL` for none.
    /// Written by `LivingEntityRenderer.updateRenderState`, read by the render pass of the same state.
    void spellEngine_setTint(int argb);
    int spellEngine_getTint();

    /// True when this state's tint has alpha below 1, so the entity needs blending-capable render layers.
    default boolean spellEngine_hasTranslucentTint() {
        return (spellEngine_getTint() >>> 24) < 0xFF;
    }
}
