package net.spell_engine.client.render;

import net.minecraft.client.render.RenderLayer;
import net.spell_engine.api.render.CustomLayers;
import net.spell_engine.api.render.LightEmission;

import java.util.Map;

public class SpellModelHelper {
    public static final Map<LightEmission, RenderLayer> LAYERS = Map.of(
            LightEmission.NONE, CustomLayers.spellObject(LightEmission.NONE),
            LightEmission.GLOW, CustomLayers.spellObject(LightEmission.GLOW),
            LightEmission.RADIATE, CustomLayers.spellObject(LightEmission.RADIATE)
    );
}
