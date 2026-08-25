package net.spell_engine.client.render;

import net.minecraft.client.MinecraftClient;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.spell_engine.api.render.CustomLayers;
import net.spell_engine.api.render.LightEmission;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.client.compatibility.ShaderCompatibility;
import net.spell_engine.client.util.Color;
import net.spell_engine.internals.delivery.Beam;
import net.spell_engine.internals.casting.SpellCaster;
import net.spell_engine.utils.TargetHelper;
import net.minecraft.entity.LivingEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import net.spell_engine.internals.delivery.LaunchGeometry;

public class BeamRenderer {
    public record LayerSet(RenderLayer inner, RenderLayer outer) { }
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

    /// Beam world-render pass. Loader-neutral: takes the pose stack, camera and partial tick from
    /// whatever event the loader fires. Fabric: `WorldRenderEvents.END_MAIN` (AFTER_TRANSLUCENT was removed in 1.21.9); NeoForge:
    /// `RenderLevelStageEvent` (AFTER_TRANSLUCENT_BLOCKS).
    public static void renderAfterTranslucent(MatrixStack matrices, Camera camera, float tickDelta) {
        VertexConsumerProvider.Immediate vcProvider = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
        renderAllInWorld(matrices, vcProvider, camera, LightmapTextureManager.MAX_LIGHT_COORDINATE, tickDelta);
    }

    public static void renderAllInWorld(MatrixStack matrices, VertexConsumerProvider.Immediate vertexConsumers, Camera camera, int light, float delta) {
        var focusedEntity = camera.getFocusedEntity();
        if (focusedEntity == null) {
            return;
        }

        var world = MinecraftClient.getInstance().world;
        if (world == null) {
            return;
        }
        var renderDistance = MinecraftClient.getInstance().options.getViewDistance().getValue() * 24; // 24 = 16 * 1.5F
        var squaredRenderDistance = renderDistance * renderDistance;
        // Any entity exposing a cast process can beam (players and summons alike)
        var casters = new ArrayList<LivingEntity>();
        for (var entity : world.getEntities()) {
            if (entity instanceof LivingEntity livingEntity
                    && entity instanceof SpellCaster.Entity holder
                    && holder.getBeam() != null
                    && entity.squaredDistanceTo(focusedEntity) < squaredRenderDistance) {
                casters.add(livingEntity);
            }
        }
        if (casters.isEmpty()) {
            return;
        }

        matrices.push();
        Vec3d camPos = camera.getCameraPos();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);
        for (var livingEntity : casters) {
            var launchHeight = LaunchGeometry.launchHeight(livingEntity);
            var offset = new Vec3d(0.0, launchHeight, LaunchGeometry.launchPointOffsetDefault);
            SpellCaster.Entity caster = (SpellCaster.Entity) livingEntity;
            matrices.push();
            var pos = new Vec3d(livingEntity.lastX, livingEntity.lastY, livingEntity.lastZ)
                    .lerp(livingEntity.getEntityPos(), delta);
            matrices.translate(pos.x, pos.y, pos.z);

            Vec3d from = livingEntity.getEntityPos().add(0, launchHeight, 0);
            var lookVector = Vec3d.ZERO;
            if (livingEntity == MinecraftClient.getInstance().player) {
                // No lerp for local player
                lookVector = Vec3d.fromPolar(livingEntity.getPitch(), livingEntity.getYaw());
            } else {
                lookVector = Vec3d.fromPolar(livingEntity.lastPitch, livingEntity.lastYaw);
                lookVector = lookVector.lerp(Vec3d.fromPolar(livingEntity.getPitch(), livingEntity.getYaw()), delta);
            }
            lookVector = lookVector.normalize();
            var beamPosition = TargetHelper.castBeam(livingEntity, lookVector, 32);
            lookVector = lookVector.multiply(beamPosition.length());
            Vec3d to = from.add(lookVector);

            var beamAppearance = caster.getBeam();
            renderBeamFromPlayer(matrices, vertexConsumers, beamAppearance,
                    from, to, offset, livingEntity.getEntityWorld().getTime(), delta);
            ((BeamEmitterEntity)livingEntity).setLastRenderedBeam(new Beam.Rendered(beamPosition, beamAppearance));
            matrices.pop();
        }
        vertexConsumers.draw();
        matrices.pop();
    }

    private static void renderBeamFromPlayer(MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider,
                                             Spell.Target.Beam beam,
                                             Vec3d from, Vec3d to, Vec3d offset, long time, float tickDelta) {
        var absoluteTime = (float)Math.floorMod(time, 40) + tickDelta;

        matrixStack.push();
        matrixStack.translate(0, offset.y, 0);

        Vec3d beamVector = to.subtract(from);
        float length = (float)beamVector.length();

        // Perform some rotation
        beamVector = beamVector.normalize();
        float n = (float)Math.acos(beamVector.y);
        float o = (float)Math.atan2(beamVector.z, beamVector.x);
        matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((1.5707964F - o) * 57.295776F));
        matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(n * 57.295776F));
        matrixStack.translate(0, offset.z, 0); // At this point everything is so rotated, we need to translate along y to move along z

        matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(absoluteTime * 2.25F - 45.0F));

        var texture = Identifier.of(beam.texture_id);
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

        matrixStack.pop();
    }


    public static void renderBeam(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                  long time, float tickDelta, float direction, boolean center,
                                  Color.IntFormat innerColor, Color.IntFormat outerColor, LayerSet renderLayers,
                                  float yOffset, float height, float width) {
        matrices.push();

        float shift = (float)Math.floorMod(time, 40) + tickDelta;
        float offset = MathHelper.fractionalPart(shift * 0.2f - (float)MathHelper.floor(shift * 0.1f)) * (- direction);

        var originalWidth = width;
        if (center) {
            renderBeamLayer(matrices, vertexConsumers.getBuffer(renderLayers.inner()),
                    innerColor.red(), innerColor.green(), innerColor.blue(), innerColor.alpha(),
                    yOffset, height,
                    0.0f, width, width, 0.0f, -width, 0.0f, 0.0f, -width,
                    0.0f, 1f, height, offset);
        }

        width = originalWidth * 1.5F;
        renderBeamLayer(matrices, vertexConsumers.getBuffer(renderLayers.outer()),
                outerColor.red(), outerColor.green(), outerColor.blue(), (int) (outerColor.alpha() * 0.75F),
                yOffset, height,
                0.0f, width, width, 0.0f, -width, 0.0f, 0.0f, -width,
                0.0f, 1.0f, height, offset * 0.9F);

        width = originalWidth * 2F;
        renderBeamLayer(matrices, vertexConsumers.getBuffer(renderLayers.outer()),
                outerColor.red(), outerColor.green(), outerColor.blue(), outerColor.alpha() / 3,
                yOffset, height,
                0.0f, width, width, 0.0f, -width, 0.0f, 0.0f, -width,
                0.0f, 1.0f, height, offset * 0.8F);
        matrices.pop();
    }

    private static void renderBeamLayer(MatrixStack matrices, VertexConsumer vertices,
                                        int red, int green, int blue, int alpha,
                                        float yOffset, float height,
                                        float x1, float z1, float x2, float z2, float x3, float z3, float x4,
                                        float z4, float u1, float u2, float v1, float v2) {
        MatrixStack.Entry matrix = matrices.peek();
        renderBeamFace(matrix, vertices, red, green, blue, alpha, yOffset, height, x1, z1, x2, z2, u1, u2, v1, v2);
        renderBeamFace(matrix, vertices, red, green, blue, alpha, yOffset, height, x4, z4, x3, z3, u1, u2, v1, v2);
        renderBeamFace(matrix, vertices, red, green, blue, alpha, yOffset, height, x2, z2, x4, z4, u1, u2, v1, v2);
        renderBeamFace(matrix, vertices, red, green, blue, alpha, yOffset, height, x3, z3, x1, z1, u1, u2, v1, v2);
    }


    private static void renderBeamFace(MatrixStack.Entry matrix, VertexConsumer vertices, int red, int green, int blue, int alpha, float yOffset, float height, float x1, float z1, float x2, float z2, float u1, float u2, float v1, float v2) {
        renderBeamVertex(matrix, vertices, red, green, blue, alpha, height, x1, z1, u2, v1);
        renderBeamVertex(matrix, vertices, red, green, blue, alpha, yOffset, x1, z1, u2, v2);
        renderBeamVertex(matrix, vertices, red, green, blue, alpha, yOffset, x2, z2, u1, v2);
        renderBeamVertex(matrix, vertices, red, green, blue, alpha, height, x2, z2, u1, v1);
    }

    /**
     * @param v the top-most coordinate of the texture region
     * @param u the left-most coordinate of the texture region
     */
    private static void renderBeamVertex(MatrixStack.Entry matrix, VertexConsumer vertices, int red, int green, int blue, int alpha, float y, float x, float z, float u, float v) {
        vertices.vertex(matrix, x, y, z)
                .color(red, green, blue, alpha)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(matrix, 0.0F, 1.0F, 0.0F);
    }
}
