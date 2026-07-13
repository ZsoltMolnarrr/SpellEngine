package net.spell_engine.api.render;

import com.google.common.base.Suppliers;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.spell_engine.client.util.Color;
import org.joml.Matrix4f;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public class CustomLayers extends RenderLayer {
    public CustomLayers(String name, VertexFormat vertexFormat, VertexFormat.DrawMode drawMode, int expectedBufferSize, boolean hasCrumbling, boolean translucent, Runnable startAction, Runnable endAction) {
        super(name, vertexFormat, drawMode, expectedBufferSize, hasCrumbling, translucent, startAction, endAction);
    }

    public static RenderLayer beam(Identifier texture, boolean cull, boolean transparent) {
        MultiPhaseParameters multiPhaseParameters = MultiPhaseParameters.builder()
                .program(BEACON_BEAM_PROGRAM)
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

    BiFunction<Identifier, Boolean, RenderLayer> BEACON_BEAM = Util.memoize((texture, affectsOutline) -> {
        MultiPhaseParameters multiPhaseParameters = RenderLayer
                .MultiPhaseParameters.builder()
                .program(BEACON_BEAM_PROGRAM)
                .cull(DISABLE_CULLING)
                .texture(new RenderPhase.Texture(texture, false, false))
                .transparency(affectsOutline ? TRANSLUCENT_TRANSPARENCY : NO_TRANSPARENCY)
                .writeMaskState(affectsOutline ? COLOR_MASK : ALL_MASK)
                .build(false);
        return of("beacon_beam", VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS, 1536, false, true, multiPhaseParameters);
    });


    protected static final Transparency BEAM_TRANSPARENCY = new Transparency("beam_transparency", () -> {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
    }, () -> {
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    });

//    @Deprecated
//    public static RenderLayer projectile(Identifier texture, boolean translucent) {
//        return projectile(texture, translucent, true);
//    }
//
//    @Deprecated
//    public static RenderLayer projectile(Identifier texture, boolean translucent, boolean emissive) {
//        MultiPhaseParameters multiPhaseParameters = MultiPhaseParameters.builder()
//                .program(emissive ? ENTITY_TRANSLUCENT_EMISSIVE_PROGRAM : ENTITY_TRANSLUCENT_PROGRAM)
//                .texture(new RenderPhase.Texture((Identifier)texture, false, false))
//                .transparency(translucent ? TRANSLUCENT_TRANSPARENCY : NO_TRANSPARENCY)
//                .cull(DISABLE_CULLING)
//                .writeMaskState(translucent ? COLOR_MASK : ALL_MASK)
//                .overlay(ENABLE_OVERLAY_COLOR)
//                .build(false);
//        return RenderLayer.of("entity_translucent_emissive", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS, 256, true, true, multiPhaseParameters);
//    }



    public static RenderLayer spellEffect(LightEmission lightEmission, boolean translucent) {
        return spellObject(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, lightEmission, translucent);
    }

    public static RenderLayer projectile(LightEmission lightEmission) {
        return spellObject(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, lightEmission, false);
    }

    public static RenderLayer create(Identifier texture, RenderPhase.ShaderProgram shaderProgram, RenderPhase.Transparency transparency,
                                     RenderPhase.Cull culling, RenderPhase.WriteMaskState writeMask, RenderPhase.Overlay overlay,
                                     Target target, boolean affectsOutline) {
        MultiPhaseParameters multiPhaseParameters = MultiPhaseParameters.builder()
                .program(shaderProgram)
                .texture(new RenderPhase.Texture(texture, false, false))
                .transparency(transparency)
                .cull(culling)
                .writeMaskState(writeMask)
                .overlay(overlay)
                .target(target)
                .build(affectsOutline);
        return RenderLayer.of("entity_translucent_emissive", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS, 256, true, true, multiPhaseParameters);
    }

    public static RenderLayer spellObject(LightEmission lightEmission) {
        switch (lightEmission) {
            case RADIATE:
                return spellObject(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, lightEmission, false);
            case GLOW_TRANSLUCENT:
                return spellObject(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, lightEmission, true);
            case GLOW:
                return spellObject(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, lightEmission, false);
            case NONE:
                break;
        }
        // Backface-culled, unlike the emissive layers above (and unlike vanilla's `getEntityTranslucent`,
        // which sets DISABLE_CULLING). Solid, non-glowing spell models are ordinary geometry: a model
        // built from zero-thickness panels carrying both an `up` and a `down` face renders those two
        // quads coplanar, and without culling they z-fight and double-blend their translucent pixels.
        // Culling picks the one facing the camera, which is what such a model is authored to expect.
        return RenderLayer.getEntityTranslucentCull(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
    }

    public static RenderLayer spellObject(Identifier texture, LightEmission lightEmission, boolean translucent) {
        RenderPhase.ShaderProgram shaderProgram = switch (lightEmission) {
            case RADIATE, GLOW_TRANSLUCENT -> ENTITY_TRANSLUCENT_EMISSIVE_PROGRAM;
            case GLOW -> BEACON_BEAM_PROGRAM;
            case NONE -> ENTITY_TRANSLUCENT_PROGRAM;
        };
        MultiPhaseParameters multiPhaseParameters = MultiPhaseParameters.builder()
                .program(shaderProgram)
                .texture(new RenderPhase.Texture(texture, false, false))
                .transparency(translucent ? TRANSLUCENT_TRANSPARENCY : NO_TRANSPARENCY)
                .cull(DISABLE_CULLING)
                .writeMaskState(translucent ? COLOR_MASK : ALL_MASK)
                .overlay(ENABLE_OVERLAY_COLOR)
                .target(PARTICLES_TARGET)
                .build(false);
        return RenderLayer.of("entity_translucent_emissive", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS, 256, true, true, multiPhaseParameters);
    }

    public static RenderLayer create(String name, VertexFormat vertexFormat, VertexFormat.DrawMode drawMode, int expectedBufferSize, boolean hasCrumbling, boolean translucent, MultiPhaseParameters phases) {
        return RenderLayer.of(name, vertexFormat, drawMode, expectedBufferSize, hasCrumbling, translucent, phases);
    }

    // MARK: Item glow

    /// Grayscale streaks, so `ColorModulator` can tint them to any color.
    /// The vanilla glint texture is deeply purple, and the glint shader multiplies by it,
    /// which would poison every color it is tinted with.
    public static final Identifier ITEM_GLOW_TEXTURE = Identifier.of("spell_engine", "textures/misc/item_glow.png");
    private static final float ITEM_GLOW_SCALE = 8F;

    /// How hard the glow is driven into the frame buffer.
    ///
    /// The additive glow cannot burn brighter than white: the frame buffer is fixed point, so the
    /// fragment is clamped to `[0, 1]` before it is blended, and no shader can push past that (which
    /// is also why an emissive program like [LightEmission#RADIATE] would buy nothing here - the item
    /// is already lit full bright).
    ///
    /// Brightness past that point is bought with coverage instead of peak: a gain above `1` drives the
    /// mid tones of the streaks up into the clamp, so more of the texture reaches full white and the
    /// streaks read broader and hotter. Raise it to glow harder, at the cost of the streaks blowing out
    /// into a flatter, more uniform sheen. Read live, so it can be tuned at runtime.
    public static float itemGlowGain = 3F;

    private static final Set<RenderLayer> itemGlowLayers = ConcurrentHashMap.newKeySet();

    /// Layers returned by [#itemGlow], which need a dedicated buffer to draw in the correct order.
    /// See `ImmediateItemGlowMixin`.
    public static boolean isItemGlowLayer(RenderLayer layer) {
        return itemGlowLayers.contains(layer);
    }

    /// Plain additive. The vanilla glint blends `SRC_COLOR, ONE`, squaring the source and
    /// dimming it into the faint shimmer it is; adding it outright is what makes this glow burn.
    private static final Transparency ITEM_GLOW_TRANSPARENCY = new Transparency("spell_engine_item_glow_transparency", () -> {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ONE);
    }, () -> {
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    });

    /// Color is baked into the layer (the glint shader takes it as a uniform, and the vertex format
    /// carries no color channel), so layers are memoized per color to keep them batchable.
    /// The set of colors is bounded by the combinations of registered glowing effects.
    private static final Function<Integer, RenderLayer> ITEM_GLOW = Util.memoize(argb -> {
        var layer = RenderLayer.of("spell_engine_item_glow",
                VertexFormats.POSITION_TEXTURE,
                VertexFormat.DrawMode.QUADS,
                1536,
                MultiPhaseParameters.builder()
                        .program(GLINT_PROGRAM)
                        .texture(new RenderPhase.Texture(ITEM_GLOW_TEXTURE, true, false))
                        .writeMaskState(COLOR_MASK)
                        .cull(DISABLE_CULLING)
                        .depthTest(EQUAL_DEPTH_TEST)
                        .transparency(ITEM_GLOW_TRANSPARENCY)
                        .texturing(itemGlowTexturing(Color.fromARGB(argb)))
                        .build(false));
        itemGlowLayers.add(layer);
        return layer;
    });

    public static RenderLayer itemGlow(Color color) {
        return ITEM_GLOW.apply((int) color.toARGB());
    }

    /// The same streaks as [#itemGlow], drawn again through an emissive program.
    ///
    /// This exists to be bloomed. Bloom is not something the game itself draws, it comes from shader
    /// packs, which apply it to what they recognize as emissive - and they recognize it by the program.
    /// The glint program is not one of them, so the shimmer alone will never bloom, no matter how bright
    /// it burns. This pass is the same [LightEmission#RADIATE] program the beams reach for when a shader
    /// pack is present, and it blooms for the same reason.
    ///
    /// The emissive program declares no `TextureMat`, so it cannot scroll its own texture the way the
    /// glint does; it samples raw `UV0`, which for an item is its slice of the block atlas, and would
    /// hold a still sliver of the streaks. The scroll is affine, and interpolating transformed UVs is the
    /// same as transforming interpolated ones, so [net.spell_engine.client.util.ItemGlowVertexConsumer]
    /// applies [#itemGlowTextureMatrix] to the UVs on the way in and lands the identical shimmer.
    ///
    /// Color rides on the vertices too (this format has a color channel, unlike the glint's), so a single
    /// layer serves every color, rather than one baked layer per color.
    private static final Supplier<RenderLayer> ITEM_GLOW_EMISSIVE = Suppliers.memoize(() -> {
        var layer = RenderLayer.of("spell_engine_item_glow_emissive",
                VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
                VertexFormat.DrawMode.QUADS,
                1536,
                MultiPhaseParameters.builder()
                        .program(ENTITY_TRANSLUCENT_EMISSIVE_PROGRAM)
                        .texture(new RenderPhase.Texture(ITEM_GLOW_TEXTURE, true, false))
                        .writeMaskState(COLOR_MASK)
                        .cull(ENABLE_CULLING)
                        .depthTest(EQUAL_DEPTH_TEST)
                        .transparency(ITEM_GLOW_TRANSPARENCY)
                        .overlay(ENABLE_OVERLAY_COLOR)
                        .lightmap(ENABLE_LIGHTMAP)
                        .build(false));
        itemGlowLayers.add(layer);
        return layer;
    });

    public static RenderLayer itemGlowEmissive() {
        return ITEM_GLOW_EMISSIVE.get();
    }

    /// Scrolls the streaks across the item, the same motion the vanilla glint uses.
    ///
    /// The glint shader applies this itself, through its `TextureMat` uniform. The emissive shader has
    /// no such uniform, so the emissive pass has to apply this to its UVs on the CPU instead - see
    /// [net.spell_engine.client.util.ItemGlowVertexConsumer]. Both read it from here, so the two passes
    /// scroll as one instead of drifting apart.
    public static Matrix4f itemGlowTextureMatrix() {
        var time = (long)(Util.getMeasuringTimeMs() * MinecraftClient.getInstance().options.getGlintSpeed().getValue() * 8.0);
        var x = (float)(time % 110000L) / 110000.0F;
        var y = (float)(time % 30000L) / 30000.0F;
        var textureMatrix = new Matrix4f().translation(-x, y, 0.0F);
        textureMatrix.rotateZ((float) (Math.PI / 18)).scale(ITEM_GLOW_SCALE);
        return textureMatrix;
    }

    private static RenderPhase.Texturing itemGlowTexturing(Color color) {
        return new RenderPhase.Texturing("spell_engine_item_glow_texturing",
                () -> {
                    RenderSystem.setTextureMatrix(itemGlowTextureMatrix());

                    // Opacity is folded into the color instead of the alpha, because the additive blend
                    // scales by color, and the glint shader discards fragments below `alpha < 0.1`,
                    // which would cut faint glows off entirely instead of fading them out.
                    var intensity = color.alpha() * itemGlowGain;
                    RenderSystem.setShaderColor(
                            color.red() * intensity,
                            color.green() * intensity,
                            color.blue() * intensity,
                            1F);

                    // The glint shader fades by this, and it follows the `Glint Strength` video setting.
                    // This glow carries gameplay meaning, so it must not be dimmed away by a cosmetic option.
                    RenderSystem.setShaderGlintAlpha(1F);
                },
                () -> {
                    RenderSystem.resetTextureMatrix();
                    RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
                    RenderSystem.setShaderGlintAlpha(MinecraftClient.getInstance().options.getGlintStrength().getValue());
                });
    }
}