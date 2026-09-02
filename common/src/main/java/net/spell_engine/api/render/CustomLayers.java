package net.spell_engine.api.render;

import com.google.common.base.Suppliers;
import org.jetbrains.annotations.Nullable;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.platform.CompareOp;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.spell_engine.SpellEngineMod;
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
/// over a {@link RenderPipeline} (shaders, blend, depth, cull, color/depth write). Since 26.1 blend and color
/// writes are a {@link ColorTargetState} and depth test/write a {@link DepthStencilState}. Blend and depth state are
/// therefore expressed as pipeline variants below (compiled lazily on first use, like vanilla's), and the
/// old `RenderPhase`/`MultiPhaseParameters` based factories are gone.
///
/// 26.2: samplers/uniforms are declared through {@link BindGroupLayouts} (`withSampler` is gone), the vertex format
/// is `withVertexBinding` + `withPrimitiveTopology`, per-layer `bufferSize` is gone, and the depth buffer is
/// **reverse-Z**: every depth compare copied from a vanilla pipeline must be re-read from the 26.2 constant
/// (`LESS_THAN_OR_EQUAL` became `GREATER_THAN_OR_EQUAL`; `DepthStencilState.DEFAULT` flipped with it).
public class CustomLayers {

    private static Identifier pipelineId(String name) {
        return Identifier.fromNamespaceAndPath(SpellEngineMod.ID, "pipeline/" + name);
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
        map.put(BEACON_BEAM_OPAQUE_NO_CULL, PipelineKind.BEACON_BEAM);
        map.put(BEACON_BEAM_TRANSLUCENT_NO_CULL, PipelineKind.BEACON_BEAM);
        map.put(BEACON_BEAM_ADDITIVE, PipelineKind.BEACON_BEAM);
        map.put(ARMOR_CUTOUT_NO_CULL_TRANSLUCENT, PipelineKind.ENTITY_TRANSLUCENT);
        map.put(itemGlowEmissivePipeline(), PipelineKind.ENTITY_EMISSIVE);
        map.put(itemGlowGlintPipeline(), PipelineKind.GLINT);
        return map;
    }

    /// `ENTITY_TRANSLUCENT` (entity shader, translucent blend, no cull) but with depth writes, for solid spell objects
    private static final RenderPipeline ENTITY_TRANSLUCENT_DEPTH_WRITE = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation(pipelineId("entity_translucent_depth_write"))
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withShaderDefine("PER_FACE_LIGHTING")
            .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withCull(false)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .build();

    /// `ENTITY_TRANSLUCENT_EMISSIVE` (emissive entity shader, translucent blend, no cull, no depth write)
    /// with depth writes, for solid glowing spell objects
    private static final RenderPipeline ENTITY_EMISSIVE_DEPTH_WRITE = RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
            .withLocation(pipelineId("entity_emissive_depth_write"))
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withShaderDefine("PER_FACE_LIGHTING")
            .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withCull(false)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .build();

    /// Beacon beam shader, **backface culling off** — vanilla's `BEACON_BEAM_OPAQUE` / `BEACON_BEAM_TRANSLUCENT`
    /// (`RenderPipelines`, 26.1.2 lines 428-437) set no cull state at all, and `RenderPipeline.Builder#build`
    /// defaults it to `cull.orElse(true)`, so both are *culled*. Vanilla gets away with that because
    /// `BeaconRenderer#renderBeam` draws a closed 4-sided box, where the two back faces are hidden anyway.
    ///
    /// Spell Engine's beams are not opaque boxes: the shells are translucent and additively stacked, and in
    /// first person the caster sits inside the outer shells, where only their back faces face the camera.
    /// Dropping those halves the beam. Every Spell Engine beam layer up to 1.9.x was built with Yarn's
    /// `DISABLE_CULLING`; the 1.21.11 pipeline port mapped the no-cull branch onto the vanilla pipelines above
    /// and silently lost it. These restore it.
    private static final RenderPipeline BEACON_BEAM_OPAQUE_NO_CULL = RenderPipeline.builder(RenderPipelines.BEACON_BEAM_SNIPPET)
            .withLocation(pipelineId("beacon_beam_opaque_no_cull"))
            .withCull(false)
            .build();
    private static final RenderPipeline BEACON_BEAM_TRANSLUCENT_NO_CULL = RenderPipeline.builder(RenderPipelines.BEACON_BEAM_SNIPPET)
            .withLocation(pipelineId("beacon_beam_translucent_no_cull"))
            .withCull(false)
            // Same blend and depth state as vanilla `RenderPipelines.BEACON_BEAM_TRANSLUCENT` (26.2: reverse-Z, GEQUAL)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .build();


    // MARK: Beams

    /// The culled variant is vanilla's own beacon-beam pipeline (culled by builder default), so it needs no
    /// custom pipeline and stays known to shader packs without an Iris assignment.
    private static final BiFunction<Identifier, Boolean, RenderType> BEAM_CULL = Util.memoize((texture, transparent) ->
            RenderType.create("spell_beam", RenderSetup.builder(transparent ? RenderPipelines.BEACON_BEAM_TRANSLUCENT : RenderPipelines.BEACON_BEAM_OPAQUE)
                    .withTexture("Sampler0", texture)
                    .sortOnUpload()
                    .createRenderSetup()));
    private static final BiFunction<Identifier, Boolean, RenderType> BEAM_NO_CULL = Util.memoize((texture, transparent) ->
            RenderType.create("spell_beam", RenderSetup.builder(transparent ? BEACON_BEAM_TRANSLUCENT_NO_CULL : BEACON_BEAM_OPAQUE_NO_CULL)
                    .withTexture("Sampler0", texture)
                    .sortOnUpload()
                    .createRenderSetup()));

    public static RenderType beam(Identifier texture, boolean cull, boolean transparent) {
        return (cull ? BEAM_CULL : BEAM_NO_CULL).apply(texture, transparent);
    }

    // MARK: Spell objects

    public static RenderType spellEffect(LightEmission lightEmission, boolean translucent) {
        return spellObject(TextureAtlas.LOCATION_BLOCKS, lightEmission, translucent);
    }

    public static RenderType projectile(LightEmission lightEmission) {
        return spellObject(TextureAtlas.LOCATION_BLOCKS, lightEmission, false);
    }

    public static RenderType spellObject(LightEmission lightEmission) {
        switch (lightEmission) {
            case RADIATE:
                return spellObject(TextureAtlas.LOCATION_BLOCKS, lightEmission, false);
            case GLOW_TRANSLUCENT:
                return spellObject(TextureAtlas.LOCATION_BLOCKS, lightEmission, true);
            case GLOW:
                return spellObject(TextureAtlas.LOCATION_BLOCKS, lightEmission, false);
            case NONE:
                break;
        }
        // Backface-culled, unlike the emissive layers above (and unlike vanilla's `entityTranslucent`,
        // which disables culling). Solid, non-glowing spell models are ordinary geometry: a model
        // built from zero-thickness panels carrying both an `up` and a `down` face renders those two
        // quads coplanar, and without culling they z-fight and double-blend their translucent pixels.
        return SPELL_OBJECT_CULL.apply(TextureAtlas.LOCATION_BLOCKS);
    }

    /// The [LightEmission#NONE] layer: vanilla `entityTranslucentCullItemTarget` semantics (26.1 folded the former
    /// `item_entity_translucent_cull` shader into `ENTITY_TRANSLUCENT_CULL` + the item-entity output target), but
    /// never part of the entity outline (a spell model riding on a glowing entity is a decorative overlay, not
    /// its body) and not crumbling-affected.
    private static final Function<Identifier, RenderType> SPELL_OBJECT_CULL = Util.memoize(texture ->
            RenderType.create("spell_object_cull", RenderSetup.builder(RenderPipelines.ENTITY_TRANSLUCENT_CULL)
                    .withTexture("Sampler0", texture)
                    .setOutputTarget(net.minecraft.client.renderer.rendertype.OutputTarget.ITEM_ENTITY_TARGET)
                    .useLightmap()
                    .useOverlay()
                    .sortOnUpload()
                    .setOutline(RenderSetup.OutlineProperty.NONE)
                    .createRenderSetup()));

    private record SpellObjectKey(Identifier texture, LightEmission lightEmission, boolean translucent) { }

    private static final Function<SpellObjectKey, RenderType> SPELL_OBJECT = Util.memoize(key -> {
        RenderPipeline pipeline = switch (key.lightEmission) {
            case RADIATE, GLOW_TRANSLUCENT -> key.translucent ? RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE : ENTITY_EMISSIVE_DEPTH_WRITE;
            // No cull, like every other emission mode (and like the pre-port `DISABLE_CULLING` layer):
            // the vanilla beacon-beam pipelines are culled, see BEACON_BEAM_*_NO_CULL above.
            case GLOW -> key.translucent ? BEACON_BEAM_TRANSLUCENT_NO_CULL : BEACON_BEAM_OPAQUE_NO_CULL;
            case NONE -> key.translucent ? RenderPipelines.ENTITY_TRANSLUCENT : ENTITY_TRANSLUCENT_DEPTH_WRITE;
        };
        var setup = RenderSetup.builder(pipeline)
                .withTexture("Sampler0", key.texture)
                .sortOnUpload()
                .setOutline(RenderSetup.OutlineProperty.NONE);
        if (key.lightEmission != LightEmission.GLOW) {
            // The beacon beam vertex format carries no overlay
            setup.useOverlay();
        }
        if (key.lightEmission == LightEmission.NONE) {
            setup.useLightmap();
        }
        return RenderType.create("spell_object", setup.createRenderSetup());
    });

    /// Beacon-beam program blended additively (`SRC_ALPHA, ONE`, the old lightning transparency), no cull,
    /// no depth write: the shader-pack variant of translucent glowing spell objects (e.g. Paladins' barrier),
    /// which used to go through the lightning program on 1.21.1 (its 1.21.11 pipeline is POSITION_COLOR only).
    private static final RenderPipeline BEACON_BEAM_ADDITIVE = RenderPipeline.builder(RenderPipelines.BEACON_BEAM_SNIPPET)
            .withLocation(pipelineId("beacon_beam_additive"))
            .withCull(false)
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            // 26.2 reverse-Z: the no-write translucent test is GEQUAL (vanilla `BEACON_BEAM_TRANSLUCENT`)
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .build();

    private static final Function<Identifier, RenderType> SPELL_OBJECT_ADDITIVE = Util.memoize(texture ->
            RenderType.create("spell_object_additive", RenderSetup.builder(BEACON_BEAM_ADDITIVE)
                    .withTexture("Sampler0", texture)
                    .sortOnUpload()
                    .setOutline(RenderSetup.OutlineProperty.NONE)
                    .createRenderSetup()));

    /// Vanilla `ARMOR_CUTOUT_NO_CULL` with translucent blending, for armor on a translucently tinted entity
    /// (see `RenderLayersMixin` / [net.spell_engine.api.effect.EntityTints]). Only used while such a tint is
    /// active; untinted armor stays on the vanilla pipeline.
    private static final RenderPipeline ARMOR_CUTOUT_NO_CULL_TRANSLUCENT = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation(pipelineId("armor_cutout_no_cull_translucent"))
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withShaderDefine("NO_OVERLAY")
            .withShaderDefine("PER_FACE_LIGHTING")
            .withCull(false)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .build();

    private static final Function<Identifier, RenderType> ARMOR_TRANSLUCENT = Util.memoize(texture ->
            RenderType.create("spell_engine_armor_cutout_no_cull_translucent", RenderSetup.builder(ARMOR_CUTOUT_NO_CULL_TRANSLUCENT)
                    .withTexture("Sampler0", texture)
                    .useLightmap()
                    .useOverlay()
                    .setLayeringTransform(net.minecraft.client.renderer.rendertype.LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .affectsCrumbling()
                    .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
                    .createRenderSetup()));

    /// The armor layer for a translucently tinted entity (same setup as `RenderLayers.armorCutoutNoCull`, blending on)
    public static RenderType armorCutoutNoCullTranslucent(Identifier texture) {
        return ARMOR_TRANSLUCENT.apply(texture);
    }

    /// Vanilla's lightning pipeline (`POSITION_COLOR`: flat vertex color, no texture, `SRC_ALPHA, ONE`) on the
    /// main target — the 1.21.1 shader-pack variant of glowing spell objects (Paladins' barrier). Being a vanilla
    /// pipeline, Iris routes it through its lightning handling rather than the full-bright beacon-beam program,
    /// which is what keeps it from blooming out. Texture/overlay/light/normal writes are dropped by the format.
    private static final Supplier<RenderType> SPELL_OBJECT_LIGHTNING = Suppliers.memoize(() ->
            RenderType.create("spell_object_lightning", RenderSetup.builder(RenderPipelines.LIGHTNING)
                    .sortOnUpload()
                    .setOutline(RenderSetup.OutlineProperty.NONE)
                    .createRenderSetup()));

    public static RenderType spellObjectLightning() {
        return SPELL_OBJECT_LIGHTNING.get();
    }

    public static RenderType spellObjectAdditive(Identifier texture) {
        return SPELL_OBJECT_ADDITIVE.apply(texture);
    }

    public static RenderType spellObject(Identifier texture, LightEmission lightEmission, boolean translucent) {
        return SPELL_OBJECT.apply(new SpellObjectKey(texture, lightEmission, translucent));
    }

    /// Escape hatch for consumers building their own layers on the 1.21.11 API
    public static RenderType create(String name, RenderSetup setup) {
        return RenderType.create(name, setup);
    }

    // MARK: Item glow

    /// Grayscale streaks, so the color modulator can tint them to any color.
    /// The vanilla glint texture is deeply purple, and the glint shader multiplies by it,
    /// which would poison every color it is tinted with.
    public static final Identifier ITEM_GLOW_TEXTURE = Identifier.fromNamespaceAndPath("spell_engine", "textures/misc/item_glow.png");
    private static final float ITEM_GLOW_SCALE = 8F;

    /// How hard the glow is driven into the frame buffer.
    ///
    /// The additive glow cannot burn brighter than white: the frame buffer is fixed point, so the
    /// fragment is clamped to `[0, 1]` before it is blended. Brightness past that point is bought with
    /// coverage instead of peak: a gain above `1` drives the mid tones of the streaks up into the clamp,
    /// so more of the texture reaches full white and the streaks read broader and hotter. Read live.
    public static float itemGlowGain = 3F;

    private static final Set<RenderType> itemGlowLayers = ConcurrentHashMap.newKeySet();
    /// Color modulator per glint glow layer, applied by `RenderLayerItemGlowMixin` when the layer is prepared
    /// (the 1.21.1 `RenderSystem.setShaderColor` is gone; `RenderType.prepare` hard-codes white).
    private static final java.util.Map<RenderType, org.joml.Vector4f> itemGlowColors = new ConcurrentHashMap<>();

    /// Layers returned by [#itemGlow] / [#itemGlowEmissive]. (Up to 26.1 these needed a dedicated `BufferSource`
    /// buffer to draw after the item; since 26.2 the feature-render phases order them, see `ItemGlowRendering`.)
    public static boolean isItemGlowLayer(RenderType layer) {
        return itemGlowLayers.contains(layer);
    }

    /// The color modulator a glint glow layer draws with, null for every other layer
    public static @Nullable org.joml.Vector4fc itemGlowColorModulator(RenderType layer) {
        return itemGlowColors.get(layer);
    }

    private static RenderType itemGlowLayer(RenderType layer) {
        itemGlowLayers.add(layer);
        return layer;
    }

    /// Scrolls the streaks across the item, the same motion the vanilla glint uses (applied by the
    /// glint shader through `TextureMat`; the emissive pass applies it on the CPU, see
    /// [net.spell_engine.client.util.ItemGlowVertexConsumer]). Both read it from here, so they scroll as one.
    public static Matrix4f itemGlowTextureMatrix() {
        // The speed factor scales the clock, not the offsets: the offsets must still wrap by exactly one
        // texture period (1.0) or the wrap is a visible snap.
        var time = (long)(Util.getMillis() * Minecraft.getInstance().options.glintSpeed().get() * 8.0 * itemGlowScrollSpeed);
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
    public static org.joml.Vector2f itemGlowUvScale(java.util.List<net.minecraft.client.resources.model.geometry.BakedQuad> quads) {
        for (var quad : quads) {
            var sprite = quad.materialInfo().sprite();
            if (sprite == null) continue;
            float spanU = sprite.getU1() - sprite.getU0();
            float spanV = sprite.getV1() - sprite.getV0();
            if (spanU <= 0F || spanV <= 0F) continue;
            float atlasWidth = sprite.contents().width() / spanU;
            float atlasHeight = sprite.contents().height() / spanV;
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
    /// must be drawn after the item (see `ItemGlowRendering`: since 26.2 the feature-render phases order it,
    /// blending custom geometry runs after the solid item pass). Do not relax it to `GEQUAL`. Direction-neutral
    /// under reverse-Z. Layout mirrors vanilla 26.2 `RenderPipelines.GLINT`.
    private static final RenderPipeline ITEM_GLOW_GLINT_PIPELINE = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .withLocation(pipelineId("item_glow_glint"))
            .withVertexShader("core/glint")
            .withFragmentShader("core/glint")
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withCull(false)
            .withDepthStencilState(new DepthStencilState(CompareOp.EQUAL, false))
            .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .build();

    private static Supplier<com.mojang.blaze3d.textures.GpuSampler> itemGlowSampler(boolean smooth) {
        // Bilinear (the 1.21.1 `blur = true` texture flag) or nearest, per the `weaponGlowSmooth` client config.
        // REPEAT is essential: the scroll offset cycles through [0, 1) and wraps, which is only seamless when the
        // texture tiles. `SamplerCache.get(FilterMode)` is the clamped overlay/lightmap sampler — with it the item
        // sits on a stretched edge texel (solid "fully lit") for seconds and snaps when the offset wraps.
        var filter = smooth ? FilterMode.LINEAR : FilterMode.NEAREST;
        return () -> RenderSystem.getSamplerCache().getSampler(AddressMode.REPEAT, AddressMode.REPEAT, filter, filter, false);
    }

    private static boolean smoothGlow() {
        var config = net.spell_engine.client.SpellEngineClient.config;
        return config == null || config.weaponGlowSmooth;
    }

    private record ItemGlowKey(int argb, boolean smooth) { }

    /// Color is baked into the layer (the glint shader takes it as a uniform, and the vertex format
    /// carries no color channel), so layers are memoized per color to keep them batchable.
    private static final Function<ItemGlowKey, RenderType> ITEM_GLOW = Util.memoize(key -> {
        var color = net.spell_engine.client.util.Color.fromARGB(key.argb);
        var layer = RenderType.create("spell_engine_item_glow", RenderSetup.builder(ITEM_GLOW_GLINT_PIPELINE)
                .withTexture("Sampler0", ITEM_GLOW_TEXTURE, itemGlowSampler(key.smooth))
                .setTextureTransform(ITEM_GLOW_TEXTURING)
                .sortOnUpload()
                .setOutline(RenderSetup.OutlineProperty.NONE)
                .createRenderSetup());
        // Opacity is folded into the color instead of the alpha, because the additive blend scales by
        // color, and the glint shader discards fragments below `alpha < 0.1`.
        var intensity = color.alpha() * itemGlowGain;
        itemGlowColors.put(layer, new org.joml.Vector4f(color.red() * intensity, color.green() * intensity, color.blue() * intensity, 1F));
        return itemGlowLayer(layer);
    });

    /// The luminance pass: gain drives the streaks up into the clamp.
    public static RenderType itemGlow(net.spell_engine.client.util.Color color) {
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
            .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
            .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
            .withCull(true)
            .withDepthStencilState(new DepthStencilState(CompareOp.EQUAL, false))
            .build();

    private static final Function<Boolean, RenderType> ITEM_GLOW_EMISSIVE = Util.memoize(smooth -> itemGlowLayer(
            RenderType.create("spell_engine_item_glow_emissive", RenderSetup.builder(ITEM_GLOW_PIPELINE)
                    .withTexture("Sampler0", ITEM_GLOW_TEXTURE, itemGlowSampler(smooth))
                    .useOverlay()
                    .useLightmap()
                    .sortOnUpload()
                    .setOutline(RenderSetup.OutlineProperty.NONE)
                    .createRenderSetup())));

    public static RenderType itemGlowEmissive() {
        return ITEM_GLOW_EMISSIVE.apply(smoothGlow());
    }

    /// Every custom `RenderPipeline` Spell Engine builds, with its Iris program category — see the
    /// declaration in `customPipelines()`; the glow pipelines are listed there too.
    static RenderPipeline itemGlowGlintPipeline() { return ITEM_GLOW_GLINT_PIPELINE; }
    static RenderPipeline itemGlowEmissivePipeline() { return ITEM_GLOW_PIPELINE; }
}
