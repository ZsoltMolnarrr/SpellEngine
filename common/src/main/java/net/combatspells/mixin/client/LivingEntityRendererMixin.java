package net.combatspells.mixin.client;

import net.combatspells.api.spell.Spell;
import net.combatspells.client.beam.BeamEmitterEntity;
import net.combatspells.client.beam.BeamRenderer;
import net.combatspells.internals.Beam;
import net.combatspells.internals.SpellCasterEntity;
import net.combatspells.internals.SpellHelper;
import net.combatspells.utils.TargetHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {

    @Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("TAIL"))
    private void render_TAIL(LivingEntity livingEntity, float f, float delta, MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int light, CallbackInfo ci) {
        var launchHeight = SpellHelper.launchHeight(livingEntity);
        var offset = new Vec3d(0.0, launchHeight, 0.15);

        if (livingEntity instanceof SpellCasterEntity caster) {
            var beamAppearance = caster.getBeam();
            if (beamAppearance != null) {
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
                var beamPosition = TargetHelper.castBeam(livingEntity, lookVector, 32);
                lookVector = lookVector.multiply(beamPosition.length());
                Vec3d to = from.add(lookVector);

                renderBeam(matrixStack, vertexConsumerProvider, beamAppearance, from, to, offset, livingEntity.world.getTime(), delta);
                ((BeamEmitterEntity)livingEntity).setLastRenderedBeam(new Beam.Rendered(beamPosition, beamAppearance));
            } else {
                ((BeamEmitterEntity)livingEntity).setLastRenderedBeam(null);
            }
        }
    }

    private static void renderBeam(MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider,
                                   Spell.Release.Target.Beam beam,
                                   Vec3d from, Vec3d to, Vec3d offset, long time, float tickDelta) {
        var absoluteTime = (float)time + tickDelta;
        matrixStack.push();
        matrixStack.translate(0, offset.y, 0);

        Vec3d beamVector = to.subtract(from);
        float length = (float)beamVector.length();

        // Perform some rotation
        beamVector = beamVector.normalize();
        float n = (float)Math.acos(beamVector.y);
        float o = (float)Math.atan2(beamVector.z, beamVector.x);
        matrixStack.multiply(Vec3f.POSITIVE_Y.getDegreesQuaternion((1.5707964F - o) * 57.295776F));
        matrixStack.multiply(Vec3f.POSITIVE_X.getDegreesQuaternion(n * 57.295776F));
        matrixStack.translate(0, offset.z, 0); // At this point everything is so rotated, we need to translate along y to move along z

        matrixStack.multiply(Vec3f.POSITIVE_Y.getDegreesQuaternion(absoluteTime * 2.25F - 45.0F));

        var texture = new Identifier(beam.texture_id);
        var color = beam.color_rgba;
        var red = (color >> 24) & 255;
        var green = (color >> 16) & 255;
        var blue = (color >> 8 ) & 255;
        var alpha = color & 255;
        // System.out.println("Beam color " + " red:" + red + " green:" + green + " blue:" + blue + " alpha:" + alpha);
        BeamRenderer.renderBeam(matrixStack, vertexConsumerProvider,
                texture, time, tickDelta, beam.flow,
                (int)red, (int)green, (int)blue, (int)alpha,
                0, length, beam.width);

        matrixStack.pop();
    }
}
