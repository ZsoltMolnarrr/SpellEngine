package net.spell_engine.api.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
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
                .transparency(transparent ? BEAM_TRANSPARENCY : NO_TRANSPARENCY)
                .writeMaskState(transparent ? ALL_MASK : ALL_MASK)
                .build(false);
        return RenderLayer.of("spell_beam",
                VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL,
                VertexFormat.DrawMode.QUADS,
                256,
                false,
                true,
                multiPhaseParameters);
    }

    protected static final Transparency BEAM_TRANSPARENCY = new Transparency("beam_transparency", () -> {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
    }, () -> {
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    });

    public static RenderLayer projectile(Identifier texture, boolean translucent) {
        return projectile(texture, translucent, true);
    }

    public static RenderLayer projectile(Identifier texture, boolean translucent, boolean emissive) {
        MultiPhaseParameters multiPhaseParameters = MultiPhaseParameters.builder()
                .shader(emissive ? ENTITY_TRANSLUCENT_EMISSIVE_SHADER : ENTITY_TRANSLUCENT_SHADER)
                .texture(new RenderPhase.Texture((Identifier)texture, false, false))
                .transparency(translucent ? TRANSLUCENT_TRANSPARENCY : NO_TRANSPARENCY)
                .cull(DISABLE_CULLING)
                .writeMaskState(translucent ? COLOR_MASK : ALL_MASK)
                .overlay(ENABLE_OVERLAY_COLOR)
                .build(false);
        return RenderLayer.of("entity_translucent_emissive", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS, 256, true, true, multiPhaseParameters);
    }
}