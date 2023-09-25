package net.spell_engine.mixin.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.effect.CustomModelStatusEffect;
import net.spell_engine.api.effect.Synchronized;
import net.spell_engine.client.beam.BeamEmitterEntity;
import net.spell_engine.client.render.BeamRenderer;
import net.spell_engine.api.render.CustomLayers;
import net.spell_engine.internals.Beam;
import net.spell_engine.internals.casting.SpellCasterEntity;
import net.spell_engine.internals.SpellHelper;
import net.spell_engine.utils.TargetHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {

    @Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("TAIL"))
    private void render_TAIL_SpellEngine(LivingEntity livingEntity, float f, float delta, MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int light, CallbackInfo ci) {
        var launchHeight = SpellHelper.launchHeight(livingEntity);
        var offset = new Vec3d(0.0, launchHeight, SpellHelper.launchPointOffsetDefault);

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

                renderBeam(matrixStack, vertexConsumerProvider, beamAppearance, from, to, offset, livingEntity.getWorld().getTime(), delta);
                ((BeamEmitterEntity)livingEntity).setLastRenderedBeam(new Beam.Rendered(beamPosition, beamAppearance));
            } else {
                ((BeamEmitterEntity)livingEntity).setLastRenderedBeam(null);
            }
        }

        var client = MinecraftClient.getInstance();
        var isRenderingClientPlayerInFirstPerson = (livingEntity == client.player && !client.gameRenderer.getCamera().isThirdPerson());
        if (!isRenderingClientPlayerInFirstPerson) {
            for (var entry: Synchronized.effectsOf(livingEntity)) {
                var effect = entry.effect();
                var amplifier = entry.amplifier();
                var renderer = CustomModelStatusEffect.rendererOf(effect);
                if (renderer != null) {
                    renderer.renderEffect(amplifier, livingEntity, delta, matrixStack, vertexConsumerProvider, light);
                }
            }
        }
    }

    private static final RenderLayer spellObjectsLayer = CustomLayers.projectile(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, false);

    private static void renderBeam(MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider,
                                   Spell.Release.Target.Beam beam,
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
