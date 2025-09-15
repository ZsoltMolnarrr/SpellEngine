package net.spell_engine.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.random.Random;
import net.spell_engine.fx.SpellEngineParticles;
import org.jetbrains.annotations.Nullable;

public class SpellUniversalParticle extends SpriteBillboardParticle  {
    private static final Random RANDOM = Random.create();
    private final SpriteProvider spriteProvider;
    private final SpellEngineParticles.MagicParticles.Motion motion;
    private boolean animated = false;
    public boolean glows = true;
    public boolean translucent = true;
    @Nullable Entity followEntity;

    SpellUniversalParticle(ClientWorld world, SpriteProvider spriteProvider, SpellEngineParticles.MagicParticles.Motion motion, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {
        super(world, x, y, z, 0.5 - RANDOM.nextDouble(), velocityY, 0.5 - RANDOM.nextDouble());
        this.spriteProvider = spriteProvider;
        this.motion = motion;

        switch (motion) {
            case FLOAT, DECELERATE -> {
                this.velocityMultiplier = 0.96F;
                this.velocityX = this.velocityX * 0.01F + velocityX;
                this.velocityY = this.velocityY * 0.01F + velocityY;
                this.velocityZ = this.velocityZ * 0.01F + velocityZ;
                this.x = this.x + (double)((this.random.nextFloat() - this.random.nextFloat()) * 0.05F);
                this.y = this.y + (double)((this.random.nextFloat() - this.random.nextFloat()) * 0.05F);
                this.z = this.z + (double)((this.random.nextFloat() - this.random.nextFloat()) * 0.05F);
                if (motion == SpellEngineParticles.MagicParticles.Motion.DECELERATE) {
                    this.velocityMultiplier *= 0.8F;
                }
                this.maxAge = (int)(8.0 / (Math.random() * 0.8 + 0.2));
            }
            case ASCEND -> {
                this.velocityMultiplier = 0.96F;
                this.gravityStrength = -0.1F;
                this.ascending = true;
                this.velocityY *= 0.2;
                if (velocityX == 0.0 && velocityZ == 0.0) {
                    this.velocityX *= 0.10000000149011612;
                    this.velocityZ *= 0.10000000149011612;
                }
                this.maxAge = (int)(8.0 / (Math.random() * 0.8 + 0.2));
            }
            case BURST -> {
                this.velocityMultiplier = 0.7f;
                this.gravityStrength = 0.5f;
                this.velocityX *= (double)0.1f;
                this.velocityY *= (double)0.1f;
                this.velocityZ *= (double)0.1f;
                this.velocityX += velocityX * 0.4;
                this.velocityY += velocityY * 0.4;
                this.velocityZ += velocityZ * 0.4;
                this.maxAge = Math.max((int)(6.0 / (Math.random() * 0.8 + 0.6)), 1);
            }
        }

        this.setSpriteForAge(spriteProvider);
        this.collidesWithWorld = false;
    }

    @Override
    public ParticleTextureSheet getType() {
        if (glows) {
            if (translucent) {
                return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
            } else {
                return ParticleTextureSheet.PARTICLE_SHEET_LIT;
            }
        } else {
            if (translucent) {
                return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
            } else {
                return ParticleTextureSheet.PARTICLE_SHEET_OPAQUE;
            }
        }
    }

    @Override
    public int getBrightness(float tint) {
        if (glows) {
            return 255;
        } else {
            return super.getBrightness(tint);
        }
    }

    public void move(double dx, double dy, double dz) {
        if (followEntity != null && !followEntity.isRemoved()) {
            dx += followEntity.getX() - followEntity.prevX;
            dy += followEntity.getY() - followEntity.prevY;
            dz += followEntity.getZ() - followEntity.prevZ;
        }
        super.move(dx, dy, dz);
    }

    @Override
    public void tick() {
        super.tick();
        if (animated) {
            this.setSpriteForAge(this.spriteProvider);
        }
    }


    // MARK: Factories

    @Environment(EnvType.CLIENT)
    public static class MagicVariant implements ParticleFactory<TemplateParticleType> {
        private final SpriteProvider spriteProvider;
        private final SpellEngineParticles.MagicParticles.Variant particleVariant;

        public MagicVariant(SpriteProvider spriteProvider, SpellEngineParticles.MagicParticles.Variant particleVariant) {
            this.spriteProvider = spriteProvider;
            this.particleVariant = particleVariant;
        }

        public Particle createParticle(TemplateParticleType particleType, ClientWorld clientWorld, double d, double e, double f, double g, double h, double i) {
            var particle = new SpellUniversalParticle(clientWorld, this.spriteProvider, particleVariant.motion(), d, e, f, g, h, i);
            particle.glows = true;
            particle.animated = particleVariant.shape().animated;
            particle.red = 1F;
            particle.green = 1F;
            particle.blue = 1F;
            particle.scale *= 0.75f;

            switch (particleVariant.shape()) {
                case SPELL, STRIPE -> {
                    particle.alpha = 1F;
                }
                default -> {
                    particle.alpha = 0.75F;
                }
            }

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

            float j = clientWorld.random.nextFloat() * 0.5F + 0.35F;
            particle.setColor(particle.red * j, particle.green * j, particle.blue * j);

            return particle;
        }
    }
}
