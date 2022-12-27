package net.spell_engine.client.render;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

public class CustomLayers extends RenderLayer {
    public CustomLayers(String name, VertexFormat vertexFormat, VertexFormat.DrawMode drawMode, int expectedBufferSize, boolean hasCrumbling, boolean translucent, Runnable startAction, Runnable endAction) {
        super(name, vertexFormat, drawMode, expectedBufferSize, hasCrumbling, translucent, startAction, endAction);
    }

    public static RenderLayer beam(Identifier texture, boolean cull, boolean transparent) {
        MultiPhaseParameters multiPhaseParameters = MultiPhaseParameters.builder()
                .shader(BEACON_BEAM_SHADER)
                .cull(cull ? ENABLE_CULLING : DISABLE_CULLING)
                .texture(new RenderPhase.Texture(texture, false, false))
                .transparency(transparent ? RenderPhase.LIGHTNING_TRANSPARENCY : NO_TRANSPARENCY)
                .writeMaskState(transparent ? COLOR_MASK : ALL_MASK)
                .build(false);
        return RenderLayer.of("spell_beam",
                VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL,
                VertexFormat.DrawMode.QUADS,
                256,
                false,
                true,
                multiPhaseParameters);
    }

    public static RenderLayer projectile(Identifier texture, boolean translucent) {
        MultiPhaseParameters multiPhaseParameters = MultiPhaseParameters.builder()
                .shader(ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                .texture(new RenderPhase.Texture((Identifier)texture, false, false))
                .transparency(translucent ? TRANSLUCENT_TRANSPARENCY : NO_TRANSPARENCY)
                .cull(DISABLE_CULLING)
                .writeMaskState(translucent ? COLOR_MASK : ALL_MASK)
                .overlay(ENABLE_OVERLAY_COLOR)
                .build(false);
        return RenderLayer.of("entity_translucent_emissive", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS, 256, true, true, multiPhaseParameters);
    }
}