package net.combatspells.mixin.client;

import net.combatspells.internals.SpellCasterEntity;
import net.combatspells.internals.SpellHelper;
import net.combatspells.utils.TargetHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {
    private static final Identifier BEAM_TEXTURE = new Identifier("textures/entity/guardian_beam.png");
    private static final Identifier BEACON_TEXTURE = new Identifier("textures/entity/beacon_beam.png");

    private static final RenderLayer LAYER;

    static {
        LAYER = RenderLayer.getBeaconBeam(BEACON_TEXTURE, false);
        //RenderLayer.getBeaconBeam(BEACON_TEXTURE, true).apply
        // var asd = RenderLayer.of(BEACON_BEAM_SHADER)
    }

    @Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("TAIL"))
    private void render_TAIL(LivingEntity livingEntity, float f, float delta, MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int light, CallbackInfo ci) {
        var launchHeight = SpellHelper.launchHeight(livingEntity);
        var offset = new Vec3d(0.0, launchHeight, 0.15);

        if (livingEntity instanceof SpellCasterEntity caster) {
            if (caster.isBeaming()) {
                var time = (float)livingEntity.world.getTime() + delta;
                Vec3d from = livingEntity.getPos().add(0, launchHeight, 0);
                var lookVector = Vec3d.ZERO;
                if (livingEntity == MinecraftClient.getInstance().player) {
                    // No lerp for local player
                    lookVector = Vec3d.fromPolar(livingEntity.getPitch(), livingEntity.getYaw());
                } else {
                    lookVector = Vec3d.fromPolar(livingEntity.prevPitch, livingEntity.prevYaw);
                    lookVector = lookVector.lerp(Vec3d.fromPolar(livingEntity.getPitch(), livingEntity.getYaw()), delta);
                }
                lookVector = lookVector.normalize();
                var length = TargetHelper.beamLength(livingEntity, lookVector, 32);
                lookVector = lookVector.multiply(length);
                Vec3d to = from.add(lookVector);

                renderBeam(matrixStack, vertexConsumerProvider, from, to, offset, time, light);
            }
        }
    }

    private static void renderBeam(MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider,
                                   Vec3d from, Vec3d to, Vec3d offset, float time, int light) {
        matrixStack.push();
        matrixStack.translate(0, offset.y, 0);

        VertexConsumer vertexConsumer = vertexConsumerProvider.getBuffer(LAYER);

        Vec3d beamVector = to.subtract(from);
        float m = (float)beamVector.length();

        // Perform some rotation
        beamVector = beamVector.normalize();
        float n = (float)Math.acos(beamVector.y);
        float o = (float)Math.atan2(beamVector.z, beamVector.x);
        matrixStack.multiply(Vec3f.POSITIVE_Y.getDegreesQuaternion((1.5707964F - o) * 57.295776F));
        matrixStack.multiply(Vec3f.POSITIVE_X.getDegreesQuaternion(n * 57.295776F));
        matrixStack.translate(0, offset.z, 0); // At this point everything is so rotated, we need to translate along y to move along z

        float j = time;
        float k = j * 0.5F % 1.0F;

        float q = j * 0.05F * -1.5F;

        int r = 64 + (int)(1 * 191.0F);
        int g = 32 + (int)(1 * 191.0F);
        int b = 128 - (int)(1 * 64.0F);
        float v = 0.2F;
        float w = 0.282F;
        float x = MathHelper.cos(q + 2.3561945F) * 0.282F;
        float y = MathHelper.sin(q + 2.3561945F) * 0.282F;
        float z = MathHelper.cos(q + 0.7853982F) * 0.282F;
        float aa = MathHelper.sin(q + 0.7853982F) * 0.282F;
        float ab = MathHelper.cos(q + 3.926991F) * 0.282F;
        float ac = MathHelper.sin(q + 3.926991F) * 0.282F;
        float ad = MathHelper.cos(q + 5.4977875F) * 0.282F;
        float ae = MathHelper.sin(q + 5.4977875F) * 0.282F;
        float af = MathHelper.cos(q + 3.1415927F) * 0.2F;
        float ag = MathHelper.sin(q + 3.1415927F) * 0.2F;
        float ah = MathHelper.cos(q + 0.0F) * 0.2F;
        float ai = MathHelper.sin(q + 0.0F) * 0.2F;
        float aj = MathHelper.cos(q + 1.5707964F) * 0.2F;
        float ak = MathHelper.sin(q + 1.5707964F) * 0.2F;
        float al = MathHelper.cos(q + 4.712389F) * 0.2F;
        float am = MathHelper.sin(q + 4.712389F) * 0.2F;

        float aq = -1.0F + k;
        float ar = m * 2.5F + aq;

        matrixStack.multiply(Vec3f.POSITIVE_Y.getDegreesQuaternion(time * 2.25F - 45.0F));

        var length = m;
        var textureId = BEAM_TEXTURE;
        var innerRadius = 0.15F;
        renderBeamLayer(matrixStack,
                vertexConsumerProvider.getBuffer(LAYER),
                r, g, b, 255F,
                0, length, 0.0F, innerRadius, innerRadius, 0.0F, -(1F)* innerRadius, 0.0F, 0.0F, -(1F)* innerRadius, 0.0F, 1.0F,
                0F, 1F);



//        renderBeacon(matrixStack, vertexConsumerProvider, BEACON_TEXTURE, 0, 1.0F, 0, 0, m, color, 0.2F, 0.25F);

//        MatrixStack.Entry entry = matrixStack.peek();
//        Matrix4f matrix4f = entry.getPositionMatrix();
//        Matrix3f matrix3f = entry.getNormalMatrix();


//        vertex(vertexConsumer, matrix4f, matrix3f, af, m, ag, r, g, b, 0.4999F, ar, light);
//        vertex(vertexConsumer, matrix4f, matrix3f, af, 0.0F, ag, r, g, b, 0.4999F, aq, light);
//        vertex(vertexConsumer, matrix4f, matrix3f, ah, 0.0F, ai, r, g, b, 0.0F, aq, light);
//        vertex(vertexConsumer, matrix4f, matrix3f, ah, m, ai, r, g, b, 0.0F, ar, light);
//        vertex(vertexConsumer, matrix4f, matrix3f, aj, m, ak, r, g, b, 0.4999F, ar, light);
//        vertex(vertexConsumer, matrix4f, matrix3f, aj, 0.0F, ak, r, g, b, 0.4999F, aq, light);
//        vertex(vertexConsumer, matrix4f, matrix3f, al, 0.0F, am, r, g, b, 0.0F, aq, light);
//        vertex(vertexConsumer, matrix4f, matrix3f, al, m, am, r, g, b, 0.0F, ar, light);
        float as = 0.0F;

//        vertex(vertexConsumer, matrix4f, matrix3f, x, m, y, r, g, b, 0.5F, as + 0.5F, light);
//        vertex(vertexConsumer, matrix4f, matrix3f, z, m, aa, r, g, b, 1.0F, as + 0.5F, light);
//        vertex(vertexConsumer, matrix4f, matrix3f, ad, m, ae, r, g, b, 1.0F, as, light);
//        vertex(vertexConsumer, matrix4f, matrix3f, ab, m, ac, r, g, b, 0.5F, as, light);

        matrixStack.pop();

    }

    private static void renderBeamLayer(MatrixStack matrices, VertexConsumer vertices, float red, float green, float blue, float alpha, int yOffset, float height, float x1, float z1, float x2, float z2, float x3, float z3, float x4, float z4, float u1, float u2, float v1, float v2) {
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f matrix4f = entry.getPositionMatrix();
        Matrix3f matrix3f = entry.getNormalMatrix();
        renderBeamFace(matrix4f, matrix3f, vertices, red, green, blue, alpha, yOffset, height, x1, z1, x2, z2, u1, u2, v1, v2);
        renderBeamFace(matrix4f, matrix3f, vertices, red, green, blue, alpha, yOffset, height, x4, z4, x3, z3, u1, u2, v1, v2);
        renderBeamFace(matrix4f, matrix3f, vertices, red, green, blue, alpha, yOffset, height, x2, z2, x4, z4, u1, u2, v1, v2);
        renderBeamFace(matrix4f, matrix3f, vertices, red, green, blue, alpha, yOffset, height, x3, z3, x1, z1, u1, u2, v1, v2);
    }

    private static void renderBeamFace(Matrix4f positionMatrix, Matrix3f normalMatrix, VertexConsumer vertices, float red, float green, float blue, float alpha, int yOffset, float height, float x1, float z1, float x2, float z2, float u1, float u2, float v1, float v2) {
        renderBeamVertex(positionMatrix, normalMatrix, vertices, red, green, blue, alpha, height, x1, z1, u2, v1);
        renderBeamVertex(positionMatrix, normalMatrix, vertices, red, green, blue, alpha, yOffset, x1, z1, u2, v2);
        renderBeamVertex(positionMatrix, normalMatrix, vertices, red, green, blue, alpha, yOffset, x2, z2, u1, v2);
        renderBeamVertex(positionMatrix, normalMatrix, vertices, red, green, blue, alpha, height, x2, z2, u1, v1);
    }

    /**
     * @param v the top-most coordinate of the texture region
     * @param u the left-most coordinate of the texture region
     */
    private static void renderBeamVertex(Matrix4f positionMatrix, Matrix3f normalMatrix, VertexConsumer vertices, float red, float green, float blue, float alpha, float y, float x, float z, float u, float v) {
        vertices.vertex(positionMatrix, x, y, z)
                .color(red, green, blue, alpha)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(15728880)
                .normal(normalMatrix, 0.0F, 1.0F, 0.0F)
                .next();
    }
}
