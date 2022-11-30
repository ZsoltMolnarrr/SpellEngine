package net.combatspells.mixin.client;

import net.combatspells.client.beam.BeamRenderer;
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
    private static final Identifier BEACON_TEXTURE = new Identifier("textures/entity/beacon_beam.png");

    private static final RenderLayer LAYER;

    static {
        LAYER = RenderLayer.getBeaconBeam(BEACON_TEXTURE, false);
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

                renderBeam(matrixStack, vertexConsumerProvider, from, to, offset, time);
            }
        }
    }

    private static void renderBeam(MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider,
                                   Vec3d from, Vec3d to, Vec3d offset, float time) {
        matrixStack.push();
        matrixStack.translate(0, offset.y, 0);

        VertexConsumer vertexConsumer = vertexConsumerProvider.getBuffer(LAYER);

        Vec3d beamVector = to.subtract(from);
        float length = (float)beamVector.length();

        // Perform some rotation
        beamVector = beamVector.normalize();
        float n = (float)Math.acos(beamVector.y);
        float o = (float)Math.atan2(beamVector.z, beamVector.x);
        matrixStack.multiply(Vec3f.POSITIVE_Y.getDegreesQuaternion((1.5707964F - o) * 57.295776F));
        matrixStack.multiply(Vec3f.POSITIVE_X.getDegreesQuaternion(n * 57.295776F));
        matrixStack.translate(0, offset.z, 0); // At this point everything is so rotated, we need to translate along y to move along z

        matrixStack.multiply(Vec3f.POSITIVE_Y.getDegreesQuaternion(time * 2.25F - 45.0F));

        var textureId = BEACON_TEXTURE;
        var width = 0.1F;
        var color = 0xFFFFFF40;

        var red = (color >> 24) & 255;
        var green = (color >> 16) & 255;
        var blue = (color >> 8 ) & 255;
        var alpha = color & 255;
        // System.out.println("Beam color " + " red:" + red + " green:" + green + " blue:" + blue + " alpha:" + alpha);
        BeamRenderer.renderBeam(matrixStack, vertexConsumerProvider, textureId,
                red, green, blue, alpha,
                0, length, width);

        matrixStack.pop();
    }
}
