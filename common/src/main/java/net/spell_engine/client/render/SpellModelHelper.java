package net.spell_engine.client.render;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.spell_engine.api.render.CustomLayers;
import net.spell_engine.api.render.LightEmission;

import java.util.Map;

public class SpellModelHelper {
    public static final Map<LightEmission, RenderType> LAYERS = Map.of(
            LightEmission.NONE, CustomLayers.spellObject(LightEmission.NONE),
            LightEmission.GLOW, CustomLayers.spellObject(LightEmission.GLOW),
            LightEmission.GLOW_TRANSLUCENT, CustomLayers.spellObject(LightEmission.GLOW_TRANSLUCENT),
            LightEmission.RADIATE, CustomLayers.spellObject(LightEmission.RADIATE)
    );
}
