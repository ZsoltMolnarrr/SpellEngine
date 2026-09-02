package net.spell_engine.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.spell_engine.api.render.CustomLayers;
import net.spell_engine.api.render.LightEmission;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.client.compatibility.ShaderCompatibility;
import net.spell_engine.client.util.Color;
import net.spell_engine.internals.delivery.Beam;
import net.spell_engine.internals.casting.SpellCaster;
import net.spell_engine.utils.TargetHelper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import net.spell_engine.internals.delivery.LaunchGeometry;

public class BeamRenderer {
    public record LayerSet(RenderType inner, RenderType outer) { }
    private static final Map<String, LayerSet> layerCache = new HashMap<>();
    public static LayerSet layerSetFor(Identifier texture, Spell.Target.Beam.Luminance luminance) {
        var key = texture.toString() + luminance.toString();
        if (layerCache.containsKey(key)) {
            return layerCache.get(key);
        } else {
            LayerSet layerSet;
            switch (luminance) {
                case LOW -> {
                    layerSet = low(texture);
                }
                case MEDIUM -> {
                    layerSet = medium(texture);
                }
                case HIGH -> {
                    layerSet = high(texture);
                }
                default -> layerSet = low(texture);
            }
            layerCache.put(key, layerSet);
            return layerSet;
        }
    }
    public static LayerSet vanilla(Identifier texture) {
        return new LayerSet(
                CustomLayers.beam(texture, false, true),
                CustomLayers.spellObject(texture, LightEmission.GLOW, true)
        );
    }
    public static LayerSet low(Identifier texture) {
        return new LayerSet(
                CustomLayers.beam(texture, false, false),
                CustomLayers.beam(texture, false, true)
        );
    }
    public static LayerSet medium(Identifier texture) {
        return new LayerSet(
                CustomLayers.spellObject(texture, LightEmission.RADIATE, false),
                CustomLayers.beam(texture, false, true)
        );
    }
    public static LayerSet high(Identifier texture) {
        return new LayerSet(
                CustomLayers.spellObject(texture, LightEmission.RADIATE, false),
                CustomLayers.spellObject(texture, LightEmission.RADIATE, true)
        );
    }

    /// Beam submission pass. Loader-neutral: takes the pose stack (camera-relative, as handed to entity submits),
    /// the level's submit node collector, the camera and the partial tick from whatever hook the loader fires.
    /// Fabric: `LevelRenderEvents.COLLECT_SUBMITS`; NeoForge: `SubmitCustomGeometryEvent`. Both run inside
    /// `LevelRenderer#submitFeatures`, i.e. the beam geometry is submitted like vanilla's beacon beam
    /// (`BeaconRenderer#submitBeaconBeam`) and drawn by the feature-render phases: the blending layers in
    /// `translucentCustomGeometry` (before translucent terrain), the opaque low-luminance core in `solid`.
    /// (26.2 removed `MultiBufferSource`; up to 26.1 this drew immediately in the after-translucent event.)
    public static void submit(PoseStack matrices, SubmitNodeCollector collector, Camera camera, float tickDelta) {
        renderAllInWorld(matrices, collector, camera, LightCoordsUtil.FULL_BRIGHT, tickDelta);
    }

    public static void renderAllInWorld(PoseStack matrices, SubmitNodeCollector vertexConsumers, Camera camera, int light, float delta) {
        var focusedEntity = camera.entity();
        if (focusedEntity == null) {
            return;
        }

        var world = Minecraft.getInstance().level;
        if (world == null) {
            return;
        }
        var renderDistance = Minecraft.getInstance().options.renderDistance().get() * 24; // 24 = 16 * 1.5F
        var squaredRenderDistance = renderDistance * renderDistance;
        // Any entity exposing a cast process can beam (players and summons alike)
        var casters = new ArrayList<LivingEntity>();
        for (var entity : world.entitiesForRendering()) {
            if (entity instanceof LivingEntity livingEntity
                    && entity instanceof SpellCaster.Entity holder
                    && holder.getBeam() != null
                    && entity.distanceToSqr(focusedEntity) < squaredRenderDistance) {
                casters.add(livingEntity);
            }
        }
        if (casters.isEmpty()) {
            return;
        }

        matrices.pushPose();
        Vec3 camPos = camera.position();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);
        for (var livingEntity : casters) {
            var launchHeight = LaunchGeometry.launchHeight(livingEntity);
            var offset = new Vec3(0.0, launchHeight, LaunchGeometry.launchPointOffsetDefault);
            SpellCaster.Entity caster = (SpellCaster.Entity) livingEntity;
            matrices.pushPose();
            // Everything below lives in one render frame: `Entity#getPosition(partialTick)` and
            // `Entity#getViewVector(partialTick)` are the same lerps vanilla's `GuardianRenderer#getPosition`
            // and `Entity#pick(range, partialTick, …)` use to place and aim a beam between ticks.
            var pos = livingEntity.getPosition(delta);
            matrices.translate(pos.x, pos.y, pos.z);

            Vec3 from = pos.add(0, launchHeight, 0);
            var lookVector = livingEntity.getViewVector(delta).normalize();
            // Cast from the point we are about to draw from, not from the tick-time launch point:
            // a raycast started a fraction of a block behind the drawn origin returns a hit distance
            // that belongs to a different frame, and the beam's far end swims against the surface.
            var launchPoint = LaunchGeometry.launchPoint(livingEntity, LaunchGeometry.launchPointOffsetDefault, delta);
            var beamPosition = TargetHelper.castBeam(livingEntity, launchPoint, lookVector, 32);
            Vec3 to = from.add(lookVector.scale(beamPosition.length()));

            var beamAppearance = caster.getBeam();
            renderBeamFromPlayer(matrices, vertexConsumers, beamAppearance,
                    from, to, offset, livingEntity.level().getGameTime(), delta);
            ((BeamEmitterEntity)livingEntity).setLastRenderedBeam(new Beam.Rendered(beamPosition, beamAppearance));
            matrices.popPose();
        }
        matrices.popPose();
    }

    private static void renderBeamFromPlayer(PoseStack matrixStack, SubmitNodeCollector vertexConsumerProvider,
                                             Spell.Target.Beam beam,
                                             Vec3 from, Vec3 to, Vec3 offset, long time, float tickDelta) {
        var absoluteTime = (float)Math.floorMod(time, 40) + tickDelta;

        matrixStack.pushPose();
        matrixStack.translate(0, offset.y, 0);

        Vec3 beamVector = to.subtract(from);
        float length = (float)beamVector.length();

        // Perform some rotation
        beamVector = beamVector.normalize();
        float n = (float)Math.acos(beamVector.y);
        float o = (float)Math.atan2(beamVector.z, beamVector.x);
        matrixStack.mulPose(Axis.YP.rotationDegrees((1.5707964F - o) * 57.295776F));
        matrixStack.mulPose(Axis.XP.rotationDegrees(n * 57.295776F));
        matrixStack.translate(0, offset.z, 0); // At this point everything is so rotated, we need to translate along y to move along z

        matrixStack.mulPose(Axis.YP.rotationDegrees(absoluteTime * 2.25F - 45.0F));

        var texture = Identifier.parse(beam.texture_id);
        var outerColor = Color.IntFormat.fromLongRGBA(beam.color_rgba);
        var innerColor = Color.IntFormat.fromLongRGBA(beam.inner_color_rgba);

        LayerSet renderLayers;
        if (ShaderCompatibility.isVanillaRenderSystem()) {
            renderLayers = vanilla(texture);
        } else {
            var luminance = ShaderCompatibility.isShaderPackInUse()
                    ? (SpellEngineClient.config.renderBeamsHighLuminance ? beam.luminance : Spell.Target.Beam.Luminance.MEDIUM)
                    : Spell.Target.Beam.Luminance.LOW;
            renderLayers = layerSetFor(texture, luminance);
        }
        BeamRenderer.renderBeam(matrixStack, vertexConsumerProvider,
                time, tickDelta, beam.flow, true,
                innerColor, outerColor, renderLayers,
                0, length, beam.width);

        matrixStack.popPose();
    }


    public static void renderBeam(PoseStack matrices, SubmitNodeCollector vertexConsumers,
                                  long time, float tickDelta, float direction, boolean center,
                                  Color.IntFormat innerColor, Color.IntFormat outerColor, LayerSet renderLayers,
                                  float yOffset, float height, float width) {
        matrices.pushPose();

        float shift = (float)Math.floorMod(time, 40) + tickDelta;
        float offset = Mth.frac(shift * 0.2f - (float)Mth.floor(shift * 0.1f)) * (- direction);

        var originalWidth = width;
        // Each shell is one custom-geometry submit on its layer; the collector copies the pose, and the lambda
        // writes the vertices when the feature renderer builds the frame (same idiom as `BeaconRenderer`).
        if (center) {
            var w = width;
            vertexConsumers.submitCustomGeometry(matrices, renderLayers.inner(), (pose, vertices) ->
                    renderBeamLayer(pose, vertices,
                            innerColor.red(), innerColor.green(), innerColor.blue(), innerColor.alpha(),
                            yOffset, height,
                            0.0f, w, w, 0.0f, -w, 0.0f, 0.0f, -w,
                            0.0f, 1f, height, offset));
        }

        var w1 = originalWidth * 1.5F;
        vertexConsumers.submitCustomGeometry(matrices, renderLayers.outer(), (pose, vertices) ->
                renderBeamLayer(pose, vertices,
                        outerColor.red(), outerColor.green(), outerColor.blue(), (int) (outerColor.alpha() * 0.75F),
                        yOffset, height,
                        0.0f, w1, w1, 0.0f, -w1, 0.0f, 0.0f, -w1,
                        0.0f, 1.0f, height, offset * 0.9F));

        var w2 = originalWidth * 2F;
        vertexConsumers.submitCustomGeometry(matrices, renderLayers.outer(), (pose, vertices) ->
                renderBeamLayer(pose, vertices,
                        outerColor.red(), outerColor.green(), outerColor.blue(), outerColor.alpha() / 3,
                        yOffset, height,
                        0.0f, w2, w2, 0.0f, -w2, 0.0f, 0.0f, -w2,
                        0.0f, 1.0f, height, offset * 0.8F));
        matrices.popPose();
    }

    private static void renderBeamLayer(PoseStack.Pose matrix, VertexConsumer vertices,
                                        int red, int green, int blue, int alpha,
                                        float yOffset, float height,
                                        float x1, float z1, float x2, float z2, float x3, float z3, float x4,
                                        float z4, float u1, float u2, float v1, float v2) {
        renderBeamFace(matrix, vertices, red, green, blue, alpha, yOffset, height, x1, z1, x2, z2, u1, u2, v1, v2);
        renderBeamFace(matrix, vertices, red, green, blue, alpha, yOffset, height, x4, z4, x3, z3, u1, u2, v1, v2);
        renderBeamFace(matrix, vertices, red, green, blue, alpha, yOffset, height, x2, z2, x4, z4, u1, u2, v1, v2);
        renderBeamFace(matrix, vertices, red, green, blue, alpha, yOffset, height, x3, z3, x1, z1, u1, u2, v1, v2);
    }


    private static void renderBeamFace(PoseStack.Pose matrix, VertexConsumer vertices, int red, int green, int blue, int alpha, float yOffset, float height, float x1, float z1, float x2, float z2, float u1, float u2, float v1, float v2) {
        renderBeamVertex(matrix, vertices, red, green, blue, alpha, height, x1, z1, u2, v1);
        renderBeamVertex(matrix, vertices, red, green, blue, alpha, yOffset, x1, z1, u2, v2);
        renderBeamVertex(matrix, vertices, red, green, blue, alpha, yOffset, x2, z2, u1, v2);
        renderBeamVertex(matrix, vertices, red, green, blue, alpha, height, x2, z2, u1, v1);
    }

    /**
     * @param v the top-most coordinate of the texture region
     * @param u the left-most coordinate of the texture region
     */
    private static void renderBeamVertex(PoseStack.Pose matrix, VertexConsumer vertices, int red, int green, int blue, int alpha, float y, float x, float z, float u, float v) {
        vertices.addVertex(matrix, x, y, z)
                .setColor(red, green, blue, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal(matrix, 0.0F, 1.0F, 0.0F);
    }
}
