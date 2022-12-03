package net.combatspells.client.beam;

import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Matrix3f;
import net.minecraft.util.math.Matrix4f;

public class BeamRenderer extends RenderLayer {
    public BeamRenderer(String name, VertexFormat vertexFormat, VertexFormat.DrawMode drawMode, int expectedBufferSize, boolean hasCrumbling, boolean translucent, Runnable startAction, Runnable endAction) {
        super(name, vertexFormat, drawMode, expectedBufferSize, hasCrumbling, translucent, startAction, endAction);
    }

    private static RenderLayer createRenderLayer(Identifier texture, boolean cull, boolean transparent) {
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

//        RenderLayer.MultiPhaseParameters multiPhaseParameters = RenderLayer.MultiPhaseParameters.builder()
//                .shader(BEACON_BEAM_SHADER)
//                .texture(new RenderPhase.Texture(texture, false, false))
//                .cull(cull ? ENABLE_CULLING : DISABLE_CULLING)
//                .overlay(ENABLE_OVERLAY_COLOR)
//                .transparency(TRANSLUCENT_TRANSPARENCY)
//                .writeMaskState(RenderPhase.ALL_MASK)
//                .build(true);
//        return RenderLayer.of("spell_beam",
//                VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL,
//                VertexFormat.DrawMode.QUADS,
//                256,
//                false,
//                true,
//                multiPhaseParameters);
    }

    public static void renderBeam(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                  Identifier texture, long time, float tickDelta, float direction,
                                  int red, int green, int blue, int alpha,
                                  float yOffset, float height, float width) {
        matrices.push();

        float shift = (float)Math.floorMod(time, 40) + tickDelta;
        float offset = MathHelper.fractionalPart(shift * 0.2f - (float)MathHelper.floor(shift * 0.1f)) * (- direction);

        var innerRenderLayer = createRenderLayer(texture, true, true); //alpha < 250);
        var outerRenderLayer = createRenderLayer(texture, false, true);

        var originalWidth = width;
        renderBeamLayer(matrices, vertexConsumers.getBuffer(innerRenderLayer),
                red, green, blue, alpha,
                yOffset, height,
                0.0f, width, width, 0.0f, -width, 0.0f, 0.0f, -width,
                0.0f, 1f, height, offset);

        width = originalWidth * 1.5F;
        renderBeamLayer(matrices, vertexConsumers.getBuffer(outerRenderLayer),
                red, green, blue, alpha / 2,
                yOffset, height,
                0.0f, width, width, 0.0f, -width, 0.0f, 0.0f, -width,
                0.0f, 1.0f, height, offset * 0.9F);

        width = originalWidth * 2F;
        renderBeamLayer(matrices, vertexConsumers.getBuffer(outerRenderLayer),
                red, green, blue, alpha / 3,
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
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f matrix4f = entry.getPositionMatrix();
        Matrix3f matrix3f = entry.getNormalMatrix();
        renderBeamFace(matrix4f, matrix3f, vertices, red, green, blue, alpha, yOffset, height, x1, z1, x2, z2, u1, u2, v1, v2);
        renderBeamFace(matrix4f, matrix3f, vertices, red, green, blue, alpha, yOffset, height, x4, z4, x3, z3, u1, u2, v1, v2);
        renderBeamFace(matrix4f, matrix3f, vertices, red, green, blue, alpha, yOffset, height, x2, z2, x4, z4, u1, u2, v1, v2);
        renderBeamFace(matrix4f, matrix3f, vertices, red, green, blue, alpha, yOffset, height, x3, z3, x1, z1, u1, u2, v1, v2);
    }


    private static void renderBeamFace(Matrix4f positionMatrix, Matrix3f normalMatrix, VertexConsumer vertices, int red, int green, int blue, int alpha, float yOffset, float height, float x1, float z1, float x2, float z2, float u1, float u2, float v1, float v2) {
        renderBeamVertex(positionMatrix, normalMatrix, vertices, red, green, blue, alpha, height, x1, z1, u2, v1);
        renderBeamVertex(positionMatrix, normalMatrix, vertices, red, green, blue, alpha, yOffset, x1, z1, u2, v2);
        renderBeamVertex(positionMatrix, normalMatrix, vertices, red, green, blue, alpha, yOffset, x2, z2, u1, v2);
        renderBeamVertex(positionMatrix, normalMatrix, vertices, red, green, blue, alpha, height, x2, z2, u1, v1);
    }

    /**
     * @param v the top-most coordinate of the texture region
     * @param u the left-most coordinate of the texture region
     */
    private static void renderBeamVertex(Matrix4f positionMatrix, Matrix3f normalMatrix, VertexConsumer vertices, int red, int green, int blue, int alpha, float y, float x, float z, float u, float v) {
        vertices.vertex(positionMatrix, x, y, z)
                .color(red, green, blue, alpha)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(15728880)
                .normal(normalMatrix, 0.0F, 1.0F, 0.0F)
                .next();
    }
}
