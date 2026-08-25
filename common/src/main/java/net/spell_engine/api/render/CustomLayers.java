package net.spell_engine.api.render;

import com.google.common.base.Suppliers;
import org.jetbrains.annotations.Nullable;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.TextureTransform;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.client.compatibility.IrisCompatibility;
import net.spell_engine.client.compatibility.ShaderCompatibility;
import org.joml.Matrix4f;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/// Spell Engine render layers.
///
/// Since 1.21.11 a render layer is a {@link RenderSetup} (textures, lightmap/overlay usage, sorting hints)
/// over a {@link RenderPipeline} (shaders, blend, depth, cull, color/depth write). Blend and depth state are
/// therefore expressed as pipeline variants below (compiled lazily on first use, like vanilla's), and the
/// old `RenderPhase`/`MultiPhaseParameters` based factories are gone.
public class CustomLayers {

    private static Identifier pipelineId(String name) {
        return Identifier.of(SpellEngineMod.ID, "pipeline/" + name);
    }

    // MARK: Pipelines

    /// Iris program category of a custom pipeline (see `IrisCompatibility.assignPipelines`)
    public enum PipelineKind { ENTITY_TRANSLUCENT, ENTITY_EMISSIVE, BEACON_BEAM, GLINT }

    /// Every custom `RenderPipeline` Spell Engine builds, with its Iris program category.
    /// Shader-pack mods (Iris) map pipelines to shader programs and warn on every draw of an unknown one.
    public static java.util.Map<RenderPipeline, PipelineKind> customPipelines() {
        var map = new java.util.LinkedHashMap<RenderPipeline, PipelineKind>();
        map.put(ENTITY_TRANSLUCENT_DEPTH_WRITE, PipelineKind.ENTITY_TRANSLUCENT);
        map.put(ENTITY_EMISSIVE_DEPTH_WRITE, PipelineKind.ENTITY_EMISSIVE);
        map.put(BEACON_BEAM_OPAQUE_CULL, PipelineKind.BEACON_BEAM);
        map.put(BEACON_BEAM_TRANSLUCENT_CULL, PipelineKind.BEACON_BEAM);
        map.put(itemGlowEmissivePipeline(), PipelineKind.ENTITY_EMISSIVE);
        map.put(itemGlowGlintPipeline(), PipelineKind.GLINT);
        return map;
    }

    /// `ENTITY_TRANSLUCENT` (entity shader, translucent blend, no cull) but with depth writes, for solid spell objects
    private static final RenderPipeline ENTITY_TRANSLUCENT_DEPTH_WRITE = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation(pipelineId("entity_translucent_depth_write"))
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withShaderDefine("PER_FACE_LIGHTING")
            .withSampler("Sampler1")
            .withoutBlend()
            .withCull(false)
            .withDepthWrite(true)
            .build();

    /// `ENTITY_TRANSLUCENT_EMISSIVE` (emissive entity shader, translucent blend, no cull, no depth write)
    /// with depth writes, for solid glowing spell objects
    private static final RenderPipeline ENTITY_EMISSIVE_DEPTH_WRITE = RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
            .withLocation(pipelineId("entity_emissive_depth_write"))
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withShaderDefine("PER_FACE_LIGHTING")
            .withSampler("Sampler1")
            .withoutBlend()
            .withCull(false)
            .withDepthWrite(true)
            .build();

    /// Beacon beam shader, backface culled variants (vanilla's are never culled)
    private static final RenderPipeline BEACON_BEAM_OPAQUE_CULL = RenderPipeline.builder(RenderPipelines.RENDERTYPE_BEACON_BEAM_SNIPPET)
            .withLocation(pipelineId("beacon_beam_opaque_cull"))
            .withCull(true)
            .build();
    private static final RenderPipeline BEACON_BEAM_TRANSLUCENT_CULL = RenderPipeline.builder(RenderPipelines.RENDERTYPE_BEACON_BEAM_SNIPPET)
            .withLocation(pipelineId("beacon_beam_translucent_cull"))
            .withCull(true)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthWrite(false)
            .build();


    // MARK: Beams

    private static final BiFunction<Identifier, Boolean, RenderLayer> BEAM_CULL = Util.memoize((texture, transparent) ->
            RenderLayer.of("spell_beam", RenderSetup.builder(transparent ? BEACON_BEAM_TRANSLUCENT_CULL : BEACON_BEAM_OPAQUE_CULL)
                    .texture("Sampler0", texture)
                    .translucent()
                    .expectedBufferSize(256)
                    .build()));
    private static final BiFunction<Identifier, Boolean, RenderLayer> BEAM_NO_CULL = Util.memoize((texture, transparent) ->
            RenderLayer.of("spell_beam", RenderSetup.builder(transparent ? RenderPipelines.BEACON_BEAM_TRANSLUCENT : RenderPipelines.BEACON_BEAM_OPAQUE)
                    .texture("Sampler0", texture)
                    .translucent()
                    .expectedBufferSize(256)
                    .build()));

    public static RenderLayer beam(Identifier texture, boolean cull, boolean transparent) {
        return (cull ? BEAM_CULL : BEAM_NO_CULL).apply(texture, transparent);
    }

    // MARK: Spell objects

    public static RenderLayer spellEffect(LightEmission lightEmission, boolean translucent) {
        return spellObject(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, lightEmission, translucent);
    }

    public static RenderLayer projectile(LightEmission lightEmission) {
        return spellObject(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, lightEmission, false);
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
        // Backface-culled, unlike the emissive layers above (and unlike vanilla's `entityTranslucent`,
        // which disables culling). Solid, non-glowing spell models are ordinary geometry: a model
        // built from zero-thickness panels carrying both an `up` and a `down` face renders those two
        // quads coplanar, and without culling they z-fight and double-blend their translucent pixels.
        return SPELL_OBJECT_CULL.apply(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
    }

    /// The [LightEmission#NONE] layer: vanilla `itemEntityTranslucentCull` semantics, but never part of the
    /// entity outline (a spell model riding on a glowing entity is a decorative overlay, not its body).
    private static final Function<Identifier, RenderLayer> SPELL_OBJECT_CULL = Util.memoize(texture ->
            RenderLayer.of("spell_object_cull", RenderSetup.builder(RenderPipelines.RENDERTYPE_ITEM_ENTITY_TRANSLUCENT_CULL)
                    .texture("Sampler0", texture)
                    .outputTarget(net.minecraft.client.render.OutputTarget.ITEM_ENTITY_TARGET)
                    .useLightmap()
                    .useOverlay()
                    .translucent()
                    .expectedBufferSize(1536)
                    .outlineMode(RenderSetup.OutlineMode.NONE)
                    .build()));

    private record SpellObjectKey(Identifier texture, LightEmission lightEmission, boolean translucent) { }

    private static final Function<SpellObjectKey, RenderLayer> SPELL_OBJECT = Util.memoize(key -> {
        RenderPipeline pipeline = switch (key.lightEmission) {
            case RADIATE, GLOW_TRANSLUCENT -> key.translucent ? RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE : ENTITY_EMISSIVE_DEPTH_WRITE;
            case GLOW -> key.translucent ? RenderPipelines.BEACON_BEAM_TRANSLUCENT : RenderPipelines.BEACON_BEAM_OPAQUE;
            case NONE -> key.translucent ? RenderPipelines.ENTITY_TRANSLUCENT : ENTITY_TRANSLUCENT_DEPTH_WRITE;
        };
        var setup = RenderSetup.builder(pipeline)
                .texture("Sampler0", key.texture)
                .translucent()
                .expectedBufferSize(256)
                .outlineMode(RenderSetup.OutlineMode.NONE);
        if (key.lightEmission != LightEmission.GLOW) {
            // The beacon beam vertex format carries no overlay
            setup.useOverlay();
        }
        if (key.lightEmission == LightEmission.NONE) {
            setup.useLightmap();
        }
        return RenderLayer.of("spell_object", setup.build());
    });

    public static RenderLayer spellObject(Identifier texture, LightEmission lightEmission, boolean translucent) {
        return SPELL_OBJECT.apply(new SpellObjectKey(texture, lightEmission, translucent));
    }

    /// Escape hatch for consumers building their own layers on the 1.21.11 API
    public static RenderLayer create(String name, RenderSetup setup) {
        return RenderLayer.of(name, setup);
    }

    // MARK: Item glow

    /// Grayscale streaks, so the color modulator can tint them to any color.
    /// The vanilla glint texture is deeply purple, and the glint shader multiplies by it,
    /// which would poison every color it is tinted with.
    public static final Identifier ITEM_GLOW_TEXTURE = Identifier.of("spell_engine", "textures/misc/item_glow.png");
    private static final float ITEM_GLOW_SCALE = 8F;

    /// How hard the glow is driven into the frame buffer.
    ///
    /// The additive glow cannot burn brighter than white: the frame buffer is fixed point, so the
    /// fragment is clamped to `[0, 1]` before it is blended. Brightness past that point is bought with
    /// coverage instead of peak: a gain above `1` drives the mid tones of the streaks up into the clamp,
    /// so more of the texture reaches full white and the streaks read broader and hotter. Read live.
    public static float itemGlowGain = 3F;

    private static final Set<RenderLayer> itemGlowLayers = ConcurrentHashMap.newKeySet();
    /// Color modulator per glint glow layer, applied by `RenderLayerItemGlowMixin` when the layer draws
    /// (the 1.21.1 `RenderSystem.setShaderColor` is gone; `RenderLayer.draw` hard-codes white).
    private static final java.util.Map<RenderLayer, org.joml.Vector4f> itemGlowColors = new ConcurrentHashMap<>();

    /// Layers returned by [#itemGlow] / [#itemGlowEmissive], which need a dedicated buffer to draw in the
    /// correct order (after the item they sit on). See `ImmediateItemGlowMixin`.
    public static boolean isItemGlowLayer(RenderLayer layer) {
        return itemGlowLayers.contains(layer);
    }

    /// The color modulator a glint glow layer draws with, null for every other layer
    public static @Nullable org.joml.Vector4fc itemGlowColorModulator(RenderLayer layer) {
        return itemGlowColors.get(layer);
    }

    private static RenderLayer itemGlowLayer(RenderLayer layer) {
        itemGlowLayers.add(layer);
        return layer;
    }

    /// Scrolls the streaks across the item, the same motion the vanilla glint uses (applied by the
    /// glint shader through `TextureMat`; the emissive pass applies it on the CPU, see
    /// [net.spell_engine.client.util.ItemGlowVertexConsumer]). Both read it from here, so they scroll as one.
    public static Matrix4f itemGlowTextureMatrix() {
        // The speed factor scales the clock, not the offsets: the offsets must still wrap by exactly one
        // texture period (1.0) or the wrap is a visible snap.
        var time = (long)(Util.getMeasuringTimeMs() * MinecraftClient.getInstance().options.getGlintSpeed().getValue() * 8.0 * itemGlowScrollSpeed);
        var x = (float)(time % 110000L) / 110000.0F;
        var y = (float)(time % 30000L) / 30000.0F;
        var textureMatrix = new Matrix4f().translation(-x, y, 0.0F);
        textureMatrix.rotateZ((float) (Math.PI / 18)).scale(ITEM_GLOW_SCALE);
        return textureMatrix;
    }

    /// The streak density the 1.21.1 glow had: its UVs were the item's slice of the *block* atlas, and the
    /// 1.21.11 `items` atlas is much smaller, so the same item spans a far larger UV range there — sampled
    /// through the same 8x scroll matrix that zooms the streaks in until the item reads as flat full bright.
    /// UVs are rescaled as if the atlas were this wide. Read live, so it can be tuned at runtime.
    public static float itemGlowReferenceAtlasWidth = 2048F;

    /// Scroll speed of the streaks relative to the vanilla glint's (the 1.21.1 glow scrolled at 1x, but over a
    /// larger UV footprint — the smaller footprint after atlas compensation crosses the item faster for the same
    /// texture-space motion). Read live, so it can be tuned at runtime.
    public static float itemGlowScrollSpeed = 0.5F;

    /// Atlas-size compensation for one item layer's quads, per axis (1,1 when no sprite can be read).
    /// Per axis matters: the `items` atlas is frequently non-square, which gives a square sprite a
    /// non-square UV footprint; a uniform factor would keep that distortion and skew the streak angle on
    /// the item (the 1.21.1 block atlas was square).
    public static org.joml.Vector2f itemGlowUvScale(java.util.List<net.minecraft.client.render.model.BakedQuad> quads) {
        for (var quad : quads) {
            var sprite = quad.sprite();
            if (sprite == null) continue;
            float spanU = sprite.getMaxU() - sprite.getMinU();
            float spanV = sprite.getMaxV() - sprite.getMinV();
            if (spanU <= 0F || spanV <= 0F) continue;
            float atlasWidth = sprite.getContents().getWidth() / spanU;
            float atlasHeight = sprite.getContents().getHeight() / spanV;
            // A sprite spans `px / atlasSize` of its atlas; shrink each axis so the span matches a square reference atlas
            return new org.joml.Vector2f(atlasWidth / itemGlowReferenceAtlasWidth, atlasHeight / itemGlowReferenceAtlasWidth);
        }
        return new org.joml.Vector2f(1F, 1F);
    }

    private static final TextureTransform ITEM_GLOW_TEXTURING = new TextureTransform("spell_engine_item_glow_texturing", CustomLayers::itemGlowTextureMatrix);

    /// The vanilla glint program (`core/glint`: texture x ColorModulator, no lighting, no vertex color),
    /// but blended plain additive. The vanilla glint blends `SRC_COLOR, ONE`, squaring the source and
    /// dimming it into the faint shimmer it is; adding it outright is what makes this glow burn.
    /// `EQUAL` depth test is the mask: it confines the streaks to the pixels the item wrote, so the pass
    /// must be drawn after the item (see `ImmediateItemGlowMixin`). Do not relax it to `LEQUAL`.
    private static final RenderPipeline ITEM_GLOW_GLINT_PIPELINE = RenderPipeline.builder(
                    RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET, RenderPipelines.FOG_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(pipelineId("item_glow_glint"))
            .withVertexShader("core/glint")
            .withFragmentShader("core/glint")
            .withSampler("Sampler0")
            .withDepthWrite(false)
            .withCull(false)
            .withDepthTestFunction(DepthTestFunction.EQUAL_DEPTH_TEST)
            .withBlend(BlendFunction.ADDITIVE)
            .withVertexFormat(VertexFormats.POSITION_TEXTURE, VertexFormat.DrawMode.QUADS)
            .build();

    private static Supplier<net.minecraft.client.gl.GpuSampler> itemGlowSampler(boolean smooth) {
        // Bilinear (the 1.21.1 `blur = true` texture flag) or nearest, per the `weaponGlowSmooth` client config.
        // REPEAT is essential: the scroll offset cycles through [0, 1) and wraps, which is only seamless when the
        // texture tiles. `SamplerCache.get(FilterMode)` is the clamped overlay/lightmap sampler — with it the item
        // sits on a stretched edge texel (solid "fully lit") for seconds and snaps when the offset wraps.
        var filter = smooth ? FilterMode.LINEAR : FilterMode.NEAREST;
        return () -> RenderSystem.getSamplerCache().get(AddressMode.REPEAT, AddressMode.REPEAT, filter, filter, false);
    }

    private static boolean smoothGlow() {
        var config = net.spell_engine.client.SpellEngineClient.config;
        return config == null || config.weaponGlowSmooth;
    }

    private record ItemGlowKey(int argb, boolean smooth) { }

    /// Color is baked into the layer (the glint shader takes it as a uniform, and the vertex format
    /// carries no color channel), so layers are memoized per color to keep them batchable.
    private static final Function<ItemGlowKey, RenderLayer> ITEM_GLOW = Util.memoize(key -> {
        var color = net.spell_engine.client.util.Color.fromARGB(key.argb);
        var layer = RenderLayer.of("spell_engine_item_glow", RenderSetup.builder(ITEM_GLOW_GLINT_PIPELINE)
                .texture("Sampler0", ITEM_GLOW_TEXTURE, itemGlowSampler(key.smooth))
                .textureTransform(ITEM_GLOW_TEXTURING)
                .translucent()
                .expectedBufferSize(1536)
                .outlineMode(RenderSetup.OutlineMode.NONE)
                .build());
        // Opacity is folded into the color instead of the alpha, because the additive blend scales by
        // color, and the glint shader discards fragments below `alpha < 0.1`.
        var intensity = color.alpha() * itemGlowGain;
        itemGlowColors.put(layer, new org.joml.Vector4f(color.red() * intensity, color.green() * intensity, color.blue() * intensity, 1F));
        return itemGlowLayer(layer);
    });

    /// The luminance pass: gain drives the streaks up into the clamp.
    public static RenderLayer itemGlow(net.spell_engine.client.util.Color color) {
        return ITEM_GLOW.apply(new ItemGlowKey((int) color.toARGB(), smoothGlow()));
    }

    /// The same streaks drawn again through an emissive program, purely to be bloomed by shader packs
    /// (they bloom what they read as emissive, by program). Only submitted while a shader pack is in use.
    /// The emissive shader applies no `TextureMat` (needs `APPLY_TEXTURE_MATRIX`), so the scroll is applied
    /// per vertex by `ItemGlowVertexConsumer`; color rides on the vertices, so one layer serves every color.
    private static final RenderPipeline ITEM_GLOW_PIPELINE = RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
            .withLocation(pipelineId("item_glow"))
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withShaderDefine("NO_CARDINAL_LIGHTING")
            .withSampler("Sampler1")
            .withBlend(BlendFunction.ADDITIVE)
            .withCull(true)
            .withDepthWrite(false)
            .withDepthTestFunction(DepthTestFunction.EQUAL_DEPTH_TEST)
            .build();

    private static final Function<Boolean, RenderLayer> ITEM_GLOW_EMISSIVE = Util.memoize(smooth -> itemGlowLayer(
            RenderLayer.of("spell_engine_item_glow_emissive", RenderSetup.builder(ITEM_GLOW_PIPELINE)
                    .texture("Sampler0", ITEM_GLOW_TEXTURE, itemGlowSampler(smooth))
                    .useOverlay()
                    .useLightmap()
                    .translucent()
                    .expectedBufferSize(1536)
                    .outlineMode(RenderSetup.OutlineMode.NONE)
                    .build())));

    public static RenderLayer itemGlowEmissive() {
        return ITEM_GLOW_EMISSIVE.apply(smoothGlow());
    }

    /// Every custom `RenderPipeline` Spell Engine builds, with its Iris program category — see the
    /// declaration in `customPipelines()`; the glow pipelines are listed there too.
    static RenderPipeline itemGlowGlintPipeline() { return ITEM_GLOW_GLINT_PIPELINE; }
    static RenderPipeline itemGlowEmissivePipeline() { return ITEM_GLOW_PIPELINE; }
}
