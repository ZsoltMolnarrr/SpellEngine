package net.spell_engine.api.render;

import com.google.common.base.Suppliers;
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
    public enum PipelineKind { ENTITY_TRANSLUCENT, ENTITY_EMISSIVE, BEACON_BEAM }

    /// Every custom `RenderPipeline` Spell Engine builds, with its Iris program category.
    /// Shader-pack mods (Iris) map pipelines to shader programs and warn on every draw of an unknown one.
    public static java.util.Map<RenderPipeline, PipelineKind> customPipelines() {
        var map = new java.util.LinkedHashMap<RenderPipeline, PipelineKind>();
        map.put(ENTITY_TRANSLUCENT_DEPTH_WRITE, PipelineKind.ENTITY_TRANSLUCENT);
        map.put(ENTITY_EMISSIVE_DEPTH_WRITE, PipelineKind.ENTITY_EMISSIVE);
        map.put(BEACON_BEAM_OPAQUE_CULL, PipelineKind.BEACON_BEAM);
        map.put(BEACON_BEAM_TRANSLUCENT_CULL, PipelineKind.BEACON_BEAM);
        map.put(ITEM_GLOW_PIPELINE, PipelineKind.ENTITY_EMISSIVE);
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

    /// The item glow: emissive entity shader drawn additively over the item, depth tested for `EQUAL`
    /// (see [#ITEM_GLOW_EMISSIVE]).
    private static final RenderPipeline ITEM_GLOW_PIPELINE = RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
            .withLocation(pipelineId("item_glow"))
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withShaderDefine("PER_FACE_LIGHTING")
            .withSampler("Sampler1")
            .withBlend(BlendFunction.ADDITIVE)
            .withCull(true)
            .withDepthWrite(false)
            .withDepthTestFunction(DepthTestFunction.EQUAL_DEPTH_TEST)
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

    /// Grayscale streaks, so the vertex color can tint them to any color.
    public static final Identifier ITEM_GLOW_TEXTURE = Identifier.of("spell_engine", "textures/misc/item_glow.png");
    private static final float ITEM_GLOW_SCALE = 8F;

    /// How hard the glow is driven into the frame buffer (coverage, not peak — see the 1.21.1 notes).
    /// Read live, so it can be tuned at runtime.
    public static float itemGlowGain = 3F;

    private static final Set<RenderLayer> itemGlowLayers = ConcurrentHashMap.newKeySet();

    /// Layers returned by [#itemGlow], drawn as an extra pass after the item they sit on.
    public static boolean isItemGlowLayer(RenderLayer layer) {
        return itemGlowLayers.contains(layer);
    }

    private static RenderLayer itemGlowLayer(RenderLayer layer) {
        itemGlowLayers.add(layer);
        if (!ShaderCompatibility.isVanillaRenderSystem()) {
            IrisCompatibility.markAsDecal(layer);
        }
        return layer;
    }

    /// The glow pass: the streak texture drawn additively over the item's quads through the emissive
    /// entity shader, so it is bloomed by shader packs. Color rides on the vertices (see
    /// [net.spell_engine.client.util.ItemGlowVertexConsumer]), so one layer serves every color.
    /// `EQUAL` depth test confines it to the pixels the item wrote; it must be submitted after the item.
    private static final Supplier<RenderLayer> ITEM_GLOW_EMISSIVE = Suppliers.memoize(() -> itemGlowLayer(
            RenderLayer.of("spell_engine_item_glow", RenderSetup.builder(ITEM_GLOW_PIPELINE)
                    .texture("Sampler0", ITEM_GLOW_TEXTURE)
                    .useOverlay()
                    .useLightmap()
                    .translucent()
                    .expectedBufferSize(1536)
                    .textureTransform(TextureTransform.GLINT_TEXTURING)
                    .outlineMode(RenderSetup.OutlineMode.NONE)
                    .build())));

    /// Since 1.21.11 there is a single glow layer: the glint program cannot be tinted any more
    /// (shader color state is gone), so the emissive pass carries both luminance and color.
    public static RenderLayer itemGlow(net.spell_engine.client.util.Color color) {
        return ITEM_GLOW_EMISSIVE.get();
    }

    public static RenderLayer itemGlowEmissive() {
        return ITEM_GLOW_EMISSIVE.get();
    }

    /// Scrolls the streaks across the item, the same motion the vanilla glint uses. Applied on the CPU
    /// to the glow pass' UVs (see [net.spell_engine.client.util.ItemGlowVertexConsumer]).
    public static Matrix4f itemGlowTextureMatrix() {
        var time = (long)(Util.getMeasuringTimeMs() * MinecraftClient.getInstance().options.getGlintSpeed().getValue() * 8.0);
        var x = (float)(time % 110000L) / 110000.0F;
        var y = (float)(time % 30000L) / 30000.0F;
        var textureMatrix = new Matrix4f().translation(-x, y, 0.0F);
        textureMatrix.rotateZ((float) (Math.PI / 18)).scale(ITEM_GLOW_SCALE);
        return textureMatrix;
    }
}
