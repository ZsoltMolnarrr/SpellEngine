package net.spell_engine.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.*;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.spell_engine.fx.SpellEngineParticles;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class SpellAreaParticle extends SpriteBillboardParticle {
    @Nullable Entity followEntity;
    public int fadeOut = 0;
    private final SpriteProvider spriteProvider;

    protected SpellAreaParticle(ClientWorld world, double d, double e, double f, double g, double h, double i, SpriteProvider spriteProvider) {
        super(world, d, e, f, g, h, i);
        this.spriteProvider = spriteProvider;
        this.setSpriteForAge(spriteProvider);
    }

    private void moveWithFollowed() {
        if (followEntity != null && !followEntity.isRemoved()) {
            this.x += followEntity.getX() - followEntity.prevX;
            this.y += followEntity.getY() - followEntity.prevY;
            this.z += followEntity.getZ() - followEntity.prevZ;
        }
    }

    @Override
    public int getBrightness(float tint) {
        return 255;
    }

    @Override
    public void move(double dx, double dy, double dz) {
        if (followEntity != null && !followEntity.isRemoved()) {
            dx += followEntity.getX() - followEntity.prevX;
            dy += followEntity.getY() - followEntity.prevY;
            dz += followEntity.getZ() - followEntity.prevZ;
        }
        this.setBoundingBox(this.getBoundingBox().offset(dx, dy, dz));
        this.repositionFromBoundingBox();
    }


    @Override
    public void tick() {
        super.tick();
//        this.prevPosX = this.x;
//        this.prevPosY = this.y;
//        this.prevPosZ = this.z;
//
//        moveWithFollowed();

        if (this.age++ >= this.maxAge) {
            this.markDead();
        } else {
            this.setSpriteForAge(this.spriteProvider);
            if (fadeOut > 0) {
                var duration = this.maxAge - this.fadeOut;
                var progress = (this.maxAge - this.age) / (float) duration;
                this.alpha = Math.min(1, progress);
            }
        }
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
        // return ParticleTextureSheet.PARTICLE_SHEET_LIT;
    }

    // Credit: Fichte (CircleGroundParticle)

    @Override
    public void buildGeometry(VertexConsumer vertexConsumer, Camera camera, float tickDelta) {
        this.setSpriteForAge(spriteProvider);

        Vec3d vec3d = camera.getPos();
        float f = (float) (MathHelper.lerp(tickDelta, this.prevPosX, this.x) - vec3d.getX());
        float g = (float) (MathHelper.lerp(tickDelta, this.prevPosY, this.y) - vec3d.getY());
        float h = (float) (MathHelper.lerp(tickDelta, this.prevPosZ, this.z) - vec3d.getZ());
        Quaternionf quaternion2;
        if (this.angle == 0.0F) {
            quaternion2 = camera.getRotation();
        } else {
            quaternion2 = new Quaternionf(camera.getRotation());
            float i = MathHelper.lerp(tickDelta, this.prevAngle, this.angle);
            quaternion2.rotateZ(i);
        }

        Vector3f vec3f = new Vector3f(-1.0F, -1.0F, 0.0F);
        vec3f.rotate(quaternion2);
        Vector3f[] Vec3fs = new Vector3f[]{new Vector3f(-1.0F, -1.0F, 0.0F), new Vector3f(-1.0F, 1.0F, 0.0F), new Vector3f(1.0F, 1.0F, 0.0F), new Vector3f(1.0F, -1.0F, 0.0F)};
        float j = this.getSize(tickDelta);

        for (int k = 0; k < 4; ++k) {
            Vector3f Vec3f2 = Vec3fs[k];
            Vec3f2.rotate(new Quaternionf().rotateXYZ((float) Math.toRadians(90f), -0f, 0f));
            Vec3f2.mul(j);
            Vec3f2.add(f, g, h);
        }

        float minU = this.getMinU();
        float maxU = this.getMaxU();
        float minV = this.getMinV();
        float maxV = this.getMaxV();
        int l = this.getBrightness(tickDelta);

        vertexConsumer.vertex(Vec3fs[0].x(), Vec3fs[0].y(), Vec3fs[0].z()).texture(maxU, maxV).color(this.red, this.green, this.blue, this.alpha).light(l);
        vertexConsumer.vertex(Vec3fs[1].x(), Vec3fs[1].y(), Vec3fs[1].z()).texture(maxU, minV).color(this.red, this.green, this.blue, this.alpha).light(l);
        vertexConsumer.vertex(Vec3fs[2].x(), Vec3fs[2].y(), Vec3fs[2].z()).texture(minU, minV).color(this.red, this.green, this.blue, this.alpha).light(l);
        vertexConsumer.vertex(Vec3fs[3].x(), Vec3fs[3].y(), Vec3fs[3].z()).texture(minU, maxV).color(this.red, this.green, this.blue, this.alpha).light(l);
    }

    @Environment(EnvType.CLIENT)
    public static class Factory implements ParticleFactory<TemplateParticleType> {

        private final SpriteProvider spriteProvider;
        private final SpellEngineParticles.Texture texture;

        public Factory(SpriteProvider spriteProvider, SpellEngineParticles.Texture texture) {
            this.spriteProvider = spriteProvider;
            this.texture = texture;
        }

        public Particle createParticle(TemplateParticleType particleType, ClientWorld clientWorld, double d, double e, double f, double g, double h, double i) {
            var particle = new SpellAreaParticle(clientWorld, d, e, f, g, h, i, this.spriteProvider);
            particle.velocityX = g;
            particle.velocityY = h;
            particle.velocityZ = i;
            particle.ascending = false;

            particle.red = 1F;
            particle.green = 1F;
            particle.blue = 1F;

            if (texture.frames() > 1) {
                particle.maxAge = texture.frames();
            } else {
                particle.maxAge = 16;
                particle.fadeOut = (int) (particle.maxAge * 0.25F);
            }
            particle.scale = 1F;

            TemplateParticleType.apply(particleType, particle);
            var appearance = particleType.getAppearance();
            if (appearance != null) {
                var color = appearance.color;
                if (color != null) {
                    particle.alpha *= appearance.color.alpha();
                }
                particle.scale *= appearance.scale;
                particle.followEntity = appearance.entityFollowed;
            }

            return particle;
        }
    }
}
