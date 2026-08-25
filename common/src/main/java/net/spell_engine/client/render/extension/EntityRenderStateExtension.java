package net.spell_engine.client.render.extension;

import net.minecraft.entity.Entity;
import org.jetbrains.annotations.Nullable;

/// Duck interface on `EntityRenderState`: the entity the state was extracted from and the tick delta,
/// for Spell Engine renderers/mixins that still need live entity data at render time
public interface EntityRenderStateExtension {
    void spellEngine_setEntity(@Nullable Entity entity, float tickDelta);
    @Nullable Entity spellEngine_getEntity();
    float spellEngine_getTickDelta();
}
