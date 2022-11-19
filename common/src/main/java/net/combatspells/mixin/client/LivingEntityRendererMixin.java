package net.combatspells.mixin.client;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
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
    private static final RenderLayer LAYER;

    static {
        LAYER = RenderLayer.getEntityCutoutNoCull(BEAM_TEXTURE);
    }

    @Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("TAIL"))
    private void render_TAIL(LivingEntity livingEntity, float f, float g, MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int i, CallbackInfo ci) {
        var casterHeight = livingEntity.getStandingEyeHeight();
        var offset = new Vec3d(0.0, (double)casterHeight, 0.0);
//        Vec3d casterPos = this.fromLerpedPosition(livingEntity, (double)casterHeight, g);
//        System.out.println("Render beam start: " + casterPos);
//        Vec3d targetPos = livingEntity.getPos().add(2,2,2); // this.fromLerpedPosition(livingEntity.getPos().add(2,2,2), (double)casterHeight, g);
//        Vec3d targetPos = this.fromLerpedPosition(livingEntity, new Vec3d(2,2,2), casterHeight, g);

        Vec3d casterPos = livingEntity.getEyePos();

        var lookVector = Vec3d.fromPolar(livingEntity.getPitch(), livingEntity.getYaw()).multiply(3);

        Vec3d targetPos = casterPos.add(lookVector);
        var time = (float)livingEntity.world.getTime() + g;
        renderBeam(matrixStack, vertexConsumerProvider, casterPos, targetPos, offset, time);

//        matrixStack.push();
//        matrixStack.translate(0.0, (double)casterHeight, 0.0);
//
//        Vec3d beamVector = targetPos.subtract(casterPos);
//
//        float m = (float)(beamVector.length() + 1.0);
//        beamVector = beamVector.normalize();
//        float n = (float)Math.acos(beamVector.y);
//        float o = (float)Math.atan2(beamVector.z, beamVector.x);
//
//        matrixStack.multiply(Vec3f.POSITIVE_Y.getDegreesQuaternion((1.5707964F - o) * 57.295776F));
//        matrixStack.multiply(Vec3f.POSITIVE_X.getDegreesQuaternion(n * 57.295776F));
//
//
//
//
//
//        float h = 1; // guardianEntity.getBeamProgress(g);
//        float j = (float)livingEntity.world.getTime() + g;
//        float k = j * 0.5F % 1.0F;
//
//        float q = j * 0.05F * -1.5F;
//        float r = h * h;
//        int s = 64 + (int)(r * 191.0F);
//        int t = 32 + (int)(r * 191.0F);
//        int u = 128 - (int)(r * 64.0F);
//        float v = 0.2F;
//        float w = 0.282F;
//        float x = MathHelper.cos(q + 2.3561945F) * 0.282F;
//        float y = MathHelper.sin(q + 2.3561945F) * 0.282F;
//        float z = MathHelper.cos(q + 0.7853982F) * 0.282F;
//        float aa = MathHelper.sin(q + 0.7853982F) * 0.282F;
//        float ab = MathHelper.cos(q + 3.926991F) * 0.282F;
//        float ac = MathHelper.sin(q + 3.926991F) * 0.282F;
//        float ad = MathHelper.cos(q + 5.4977875F) * 0.282F;
//        float ae = MathHelper.sin(q + 5.4977875F) * 0.282F;
//        float af = MathHelper.cos(q + 3.1415927F) * 0.2F;
//        float ag = MathHelper.sin(q + 3.1415927F) * 0.2F;
//        float ah = MathHelper.cos(q + 0.0F) * 0.2F;
//        float ai = MathHelper.sin(q + 0.0F) * 0.2F;
//        float aj = MathHelper.cos(q + 1.5707964F) * 0.2F;
//        float ak = MathHelper.sin(q + 1.5707964F) * 0.2F;
//        float al = MathHelper.cos(q + 4.712389F) * 0.2F;
//        float am = MathHelper.sin(q + 4.712389F) * 0.2F;
//        float ao = 0.0F;
//        float ap = 0.4999F;
//        float aq = -1.0F + k;
//        float ar = m * 2.5F + aq;
//        VertexConsumer vertexConsumer = vertexConsumerProvider.getBuffer(LAYER);
//        MatrixStack.Entry entry = matrixStack.peek();
//        Matrix4f matrix4f = entry.getPositionMatrix();
//        Matrix3f matrix3f = entry.getNormalMatrix();
//        vertex(vertexConsumer, matrix4f, matrix3f, af, m, ag, s, t, u, 0.4999F, ar);
//        vertex(vertexConsumer, matrix4f, matrix3f, af, 0.0F, ag, s, t, u, 0.4999F, aq);
//        vertex(vertexConsumer, matrix4f, matrix3f, ah, 0.0F, ai, s, t, u, 0.0F, aq);
//        vertex(vertexConsumer, matrix4f, matrix3f, ah, m, ai, s, t, u, 0.0F, ar);
//        vertex(vertexConsumer, matrix4f, matrix3f, aj, m, ak, s, t, u, 0.4999F, ar);
//        vertex(vertexConsumer, matrix4f, matrix3f, aj, 0.0F, ak, s, t, u, 0.4999F, aq);
//        vertex(vertexConsumer, matrix4f, matrix3f, al, 0.0F, am, s, t, u, 0.0F, aq);
//        vertex(vertexConsumer, matrix4f, matrix3f, al, m, am, s, t, u, 0.0F, ar);
//        float as = 0.0F;
//
//        vertex(vertexConsumer, matrix4f, matrix3f, x, m, y, s, t, u, 0.5F, as + 0.5F);
//        vertex(vertexConsumer, matrix4f, matrix3f, z, m, aa, s, t, u, 1.0F, as + 0.5F);
//        vertex(vertexConsumer, matrix4f, matrix3f, ad, m, ae, s, t, u, 1.0F, as);
//        vertex(vertexConsumer, matrix4f, matrix3f, ab, m, ac, s, t, u, 0.5F, as);
//
//        matrixStack.pop();
    }

    private Vec3d fromLerpedPosition(LivingEntity entity, double yOffset, float delta) {
        double d = MathHelper.lerp((double)delta, entity.lastRenderX, entity.getX());
        double e = MathHelper.lerp((double)delta, entity.lastRenderY, entity.getY()) + yOffset;
        double f = MathHelper.lerp((double)delta, entity.lastRenderZ, entity.getZ());
        return new Vec3d(d, e, f);
    }

    private Vec3d fromLerpedPosition(LivingEntity entity, Vec3d pos, double yOffset, float delta) {
        double d = MathHelper.lerp((double)delta, entity.lastRenderX, entity.getX() + pos.x);
        double e = MathHelper.lerp((double)delta, entity.lastRenderY, entity.getY() + pos.y) + yOffset;
        double f = MathHelper.lerp((double)delta, entity.lastRenderZ, entity.getZ() + pos.z);
        return new Vec3d(d, e, f);
    }

    private static void renderBeam(MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider,
                                   Vec3d from, Vec3d to, Vec3d offset, float time) {
        matrixStack.push();
        matrixStack.translate(offset.x, offset.y, offset.z);

        Vec3d beamVector = to.subtract(from);
        float m = (float)(beamVector.length() + 1.0);
//        beamVector = beamVector.normalize();
//        float n = (float)Math.acos(beamVector.y);
//        float o = (float)Math.atan2(beamVector.z, beamVector.x);

        // Perform some rotation
        beamVector = beamVector.normalize();
        float n = (float)Math.acos(beamVector.y);
        float o = (float)Math.atan2(beamVector.z, beamVector.x);
        matrixStack.multiply(Vec3f.POSITIVE_Y.getDegreesQuaternion((1.5707964F - o) * 57.295776F));
        matrixStack.multiply(Vec3f.POSITIVE_X.getDegreesQuaternion(n * 57.295776F));

        float h = 1; // guardianEntity.getBeamProgress(g);
        float j = time;
        float k = j * 0.5F % 1.0F;

        float q = j * 0.05F * -1.5F;
        float jj = h * h;

        int r = 64 + (int)(jj * 191.0F);
        int g = 32 + (int)(jj * 191.0F);
        int b = 128 - (int)(jj * 64.0F);
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
        float ao = 0.0F;
        float ap = 0.4999F;
        float aq = -1.0F + k;
        float ar = m * 2.5F + aq;
        VertexConsumer vertexConsumer = vertexConsumerProvider.getBuffer(LAYER);
        MatrixStack.Entry entry = matrixStack.peek();
        Matrix4f matrix4f = entry.getPositionMatrix();
        Matrix3f matrix3f = entry.getNormalMatrix();
        vertex(vertexConsumer, matrix4f, matrix3f, af, m, ag, r, g, b, 0.4999F, ar);
        vertex(vertexConsumer, matrix4f, matrix3f, af, 0.0F, ag, r, g, b, 0.4999F, aq);
        vertex(vertexConsumer, matrix4f, matrix3f, ah, 0.0F, ai, r, g, b, 0.0F, aq);
        vertex(vertexConsumer, matrix4f, matrix3f, ah, m, ai, r, g, b, 0.0F, ar);
        vertex(vertexConsumer, matrix4f, matrix3f, aj, m, ak, r, g, b, 0.4999F, ar);
        vertex(vertexConsumer, matrix4f, matrix3f, aj, 0.0F, ak, r, g, b, 0.4999F, aq);
        vertex(vertexConsumer, matrix4f, matrix3f, al, 0.0F, am, r, g, b, 0.0F, aq);
        vertex(vertexConsumer, matrix4f, matrix3f, al, m, am, r, g, b, 0.0F, ar);
        float as = 0.0F;

        vertex(vertexConsumer, matrix4f, matrix3f, x, m, y, r, g, b, 0.5F, as + 0.5F);
        vertex(vertexConsumer, matrix4f, matrix3f, z, m, aa, r, g, b, 1.0F, as + 0.5F);
        vertex(vertexConsumer, matrix4f, matrix3f, ad, m, ae, r, g, b, 1.0F, as);
        vertex(vertexConsumer, matrix4f, matrix3f, ab, m, ac, r, g, b, 0.5F, as);

        matrixStack.pop();

    }

    private static void vertex(VertexConsumer vertexConsumer, Matrix4f positionMatrix, Matrix3f normalMatrix, float x, float y, float z, int red, int green, int blue, float u, float v) {
        vertexConsumer.vertex(positionMatrix, x, y, z).color(red, green, blue, 255).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(normalMatrix, 0.0F, 1.0F, 0.0F).next();
    }
}
