package net.spell_engine.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.*;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.spell_engine.fx.SpellEngineParticles;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class SpellAreaParticle extends SpriteBillboardParticle {
    @Nullable Entity followEntity;
    public SpellEngineParticles.Fading fading = SpellEngineParticles.Fading.NONE;
    public SpellEngineParticles.Orientation orientation = SpellEngineParticles.Orientation.HORIZONTAL;
    private final SpriteProvider spriteProvider;
    private float initialAlpha = 1F;
    private float initialScale = 1F;
    private Vec3d ownerPositionDiff = Vec3d.ZERO;
    private boolean skipRender = false;
    private boolean scaleWithEntity = false;

    protected SpellAreaParticle(ClientWorld world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, SpriteProvider spriteProvider) {
        super(world, x, y, z, velocityX, velocityY, velocityZ);
        this.velocityX = velocityX;
        this.velocityY = velocityY;
        this.velocityZ = velocityZ;

        this.spriteProvider = spriteProvider;
        this.setSpriteForAge(spriteProvider);
    }

    private void postInit() {
        initialAlpha = this.alpha;
        initialScale = this.scale;
        if (followEntity != null) {
            ownerPositionDiff = new Vec3d(
                    this.x - followEntity.getX(),
                    this.y - followEntity.getY(),
                    this.z - followEntity.getZ()
            );
            checkSkip();
        }
    }

    @Override
    public int getBrightness(float tint) {
        return 255;
    }

    @Override
    public void move(double dx, double dy, double dz) {
        if (followEntity != null && !followEntity.isRemoved()) {

            // Updating diff with velocity, otherwise the movement would be cancelled, due to force following
            this.ownerPositionDiff = ownerPositionDiff.add(dx, dy, dz);

            var newPos = followEntity.getPos().add(ownerPositionDiff);
            this.setPos(newPos.x, newPos.y, newPos.z);
        } else {
            this.setBoundingBox(this.getBoundingBox().offset(dx, dy, dz));
            this.repositionFromBoundingBox();
        }
    }


    @Override
    public void tick() {
        super.tick();
        checkSkip();
        if (this.age >= this.maxAge) {
            // this.markDead();
        } else {
            this.setSpriteForAge(this.spriteProvider);
            this.updateAlpha(fading, initialAlpha);
            this.updateScale();
        }
    }

    private void checkSkip() {
        this.skipRender = this.orientation == SpellEngineParticles.Orientation.VERTICAL
                && (this.followEntity == MinecraftClient.getInstance().getCameraEntity())
                && MinecraftClient.getInstance().options.getPerspective().isFirstPerson();
    }

    private void updateScale() {
        if (this.scaleWithEntity && this.followEntity != null && !this.followEntity.isRemoved() && this.followEntity instanceof LivingEntity livingEntity) {
            this.scale = initialScale * livingEntity.getScale();
        } else {
            this.scale = initialScale;
        }
    }

    private static final float EASE_DURATION = 0.3F;
    private static final float EASE_DURATION_INVERSE = 1 / EASE_DURATION;
    private void updateAlpha(SpellEngineParticles.Fading fading, float initialAlpha) {
        switch (fading) {
            case NONE -> {
                return;
            }
            case IN -> {
                var progress = this.age / ((float)this.maxAge);
                this.alpha = initialAlpha * Math.min(1, progress * EASE_DURATION_INVERSE);
            }
            case OUT -> {
                var progress = this.age / ((float)this.maxAge);
                this.alpha = initialAlpha * Math.min(1, (1 - progress) * EASE_DURATION_INVERSE);
            }
            case IN_OUT -> {
                var progress = this.age / ((float)this.maxAge);
                this.alpha = initialAlpha * Math.min(1, Math.min(progress, 1 - progress) * EASE_DURATION_INVERSE);
            }
        }
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
        // return ParticleTextureSheet.PARTICLE_SHEET_LIT;
    }

    @Override
    public void buildGeometry(VertexConsumer vertexConsumer, Camera camera, float tickDelta) {
        if (this.skipRender) {
            return;
        }
        switch (orientation) {
            case HORIZONTAL -> {
                this.buildHorizontalGeometry(vertexConsumer, camera, tickDelta);
            }
            case VERTICAL -> {
                super.buildGeometry(vertexConsumer, camera, tickDelta);
            }
        }
    }

    // Credit: Fichte (CircleGroundParticle)
    private void buildHorizontalGeometry(VertexConsumer vertexConsumer, Camera camera, float tickDelta) {
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
        private final SpellEngineParticles.Fading fading;
        private final SpellEngineParticles.Orientation orientation;
        private final boolean isAura;

        public Factory(SpriteProvider spriteProvider, SpellEngineParticles.Texture texture, SpellEngineParticles.Fading fading, SpellEngineParticles.Orientation orientation) {
            this.spriteProvider = spriteProvider;
            this.texture = texture;
            this.fading = fading;
            this.orientation = orientation;
            this.isAura = texture.id().toString().contains("aura");
        }

        public Particle createParticle(TemplateParticleType particleType, ClientWorld clientWorld, double d, double e, double f, double g, double h, double i) {
            var particle = new SpellAreaParticle(clientWorld, d, e, f, g, h, i, this.spriteProvider);
            particle.velocityX = g;
            particle.velocityY = h;
            particle.velocityZ = i;
            particle.ascending = false;
            particle.orientation = this.orientation;

            particle.red = 1F;
            particle.green = 1F;
            particle.blue = 1F;

            if (texture.frames() > 1) {
                particle.maxAge = texture.frames();
            } else {
                particle.maxAge = 16;
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
                particle.maxAge = (int) (particle.maxAge * appearance.max_age);
                particle.followEntity = appearance.entityFollowed;
            }

            particle.fading = fading;
            particle.scaleWithEntity = isAura;
            particle.postInit();
            return particle;
        }
    }
}
