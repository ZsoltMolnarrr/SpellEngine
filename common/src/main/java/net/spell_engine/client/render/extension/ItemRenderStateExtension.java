package net.spell_engine.client.render.extension;

import net.spell_engine.client.util.Color;
import org.jetbrains.annotations.Nullable;

/// Duck interface on `ItemRenderState`: the glow the holder casts on the item, resolved at update time
public interface ItemRenderStateExtension {
    void spellEngine_setGlow(@Nullable Color glow);
    @Nullable Color spellEngine_getGlow();
}
