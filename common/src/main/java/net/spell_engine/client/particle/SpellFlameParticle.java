package net.spell_engine.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.Vec3d;
import net.spell_engine.client.util.Color;
import net.spell_engine.fx.SpellEngineParticles;
import org.jetbrains.annotations.Nullable;

@Environment(value= EnvType.CLIENT)
public class SpellFlameParticle extends AbstractSlowingParticle implements AppearanceAware {
    boolean glow = true;
    boolean translucent = false;
    private SpriteProvider spriteProviderForAnimation = null;
    @Nullable Entity followEntity;
    private Vec3d ownerPositionDiff = Vec3d.ZERO;

    public SpellFlameParticle(ClientWorld clientWorld, double d, double e, double f, double g, double h, double i) {
        super(clientWorld, d, e, f, g, h, i);
    }

    @Override
    public ParticleTextureSheet getType() {
        return translucent
                ? ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT
                : ParticleTextureSheet.PARTICLE_SHEET_LIT;
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
    public float getSize(float tickDelta) {
        float f = ((float) this.age + tickDelta) / (float) this.maxAge;
        return this.scale * (1.0f - f * f * 0.5f);
    }

    @Override
    public int getBrightness(float tint) {
        if (glow) {
            return 255;
        } else {
            return super.getBrightness(tint);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.spriteProviderForAnimation != null) {
            this.setSpriteForAge(this.spriteProviderForAnimation);
        }
//        moveWithFollowed();
    }

    private void moveWithFollowed() {
        if (followEntity != null && !followEntity.isRemoved()) {
            this.x += followEntity.getX() - followEntity.prevX;
            this.y += followEntity.getY() - followEntity.prevY;
            this.z += followEntity.getZ() - followEntity.prevZ;
        }
    }

    // MARK: AppearanceAware

    @Override
    public void applyColor(Color color) {
        this.setColor(color.red(), color.green(), color.blue());
    }

    @Override
    public void multiplyAlpha(float factor) {
        this.alpha *= factor;
    }

    @Override
    public void multiplyScale(float factor) {
        this.scale *= factor;
    }

    @Override
    public void multiplyMaxAge(float factor) {
        this.maxAge = (int) (this.maxAge * factor);
    }

    @Override
    public void follow(@Nullable Entity entity) {
        this.followEntity = entity;
    }

    // MARK: Factories

    public static Config config() {
        return new Config();
    }

    public static class Config {
        boolean animated = false;
        @Nullable Color color = null;
        boolean randomDarken = false;
        @Nullable Float scale = null;
        float maxAgeFactor = 1F;
        @Nullable Float alpha = null;
        @Nullable Boolean glow = null;
        @Nullable Float velocityMultiplier = null;
        @Nullable Float gravityStrength = null;

        public Config animated() { this.animated = true; return this; }
        public Config color(Color color) { this.color = color; return this; }
        public Config randomDarken() { this.randomDarken = true; return this; }
        public Config scale(float scale) { this.scale = scale; return this; }
        public Config maxAgeFactor(float maxAgeFactor) { this.maxAgeFactor = maxAgeFactor; return this; }
        public Config alpha(float alpha) { this.alpha = alpha; return this; }
        public Config glow(boolean glow) { this.glow = glow; return this; }
        public Config velocityMultiplier(float velocityMultiplier) { this.velocityMultiplier = velocityMultiplier; return this; }
        public Config gravityStrength(float gravityStrength) { this.gravityStrength = gravityStrength; return this; }
    }

    @Environment(EnvType.CLIENT)
    public static class ConfiguredFactory implements ParticleFactory<SimpleParticleType> {
        private final SpriteProvider spriteProvider;
        private final Config config;

        public ConfiguredFactory(SpriteProvider spriteProvider, Config config) {
            this.spriteProvider = spriteProvider;
            this.config = config;
        }

        public Particle createParticle(SimpleParticleType SimpleParticleType, ClientWorld clientWorld, double d, double e, double f, double g, double h, double i) {
            var particle = new SpellFlameParticle(clientWorld, d, e, f, g, h, i);
            particle.setSprite(this.spriteProvider);
            if (config.animated) {
                particle.spriteProviderForAnimation = this.spriteProvider;
            }
            var color = config.color;
            if (color != null) {
                if (config.randomDarken) {
                    float j = clientWorld.random.nextFloat() * 0.5F + 0.35F;
                    particle.setColor(color.red() * j, color.green() * j, color.blue() * j);
                } else {
                    particle.setColor(color.red(), color.green(), color.blue());
                }
            }
            if (config.scale != null) {
                particle.scale = config.scale;
            }
            if (config.maxAgeFactor != 1F) {
                particle.maxAge = (int) (particle.maxAge * config.maxAgeFactor);
            }
            if (config.alpha != null) {
                particle.setAlpha(config.alpha);
            }
            if (config.glow != null) {
                particle.glow = config.glow;
            }
            if (config.velocityMultiplier != null) {
                particle.velocityMultiplier = config.velocityMultiplier;
            }
            if (config.gravityStrength != null) {
                particle.gravityStrength = config.gravityStrength;
            }
            return particle;
        }
    }

    @Environment(EnvType.CLIENT)
    public static class GlowingTemplateFactory implements ParticleFactory<TemplateParticleType> {
        private final SpriteProvider spriteProvider;
        private final SpellEngineParticles.Texture texture;

        public GlowingTemplateFactory(SpriteProvider spriteProvider, SpellEngineParticles.Texture texture) {
            this.spriteProvider = spriteProvider;
            this.texture = texture;
        }

        @Override
        public @Nullable Particle createParticle(TemplateParticleType particleType, ClientWorld clientWorld, double d, double e, double f, double g, double h, double i) {
            var particle = new SpellFlameParticle(clientWorld, d, e, f, g, h, i);
            particle.setSprite(this.spriteProvider);
            particle.spriteProviderForAnimation = this.spriteProvider;

            particle.setAlpha(1F);
            // glow = true by default in SpellFlameParticle
            particle.scale = 1F;
            particle.maxAge = texture.frames() > 1 ? texture.frames() : 40;

            particle.applyAppearance(particleType.getAppearance());
            return particle;
        }
    }

    @Environment(EnvType.CLIENT)
    public static class SmokeFactory implements ParticleFactory<TemplateParticleType> {
        private final SpriteProvider spriteProvider;

        public SmokeFactory(SpriteProvider spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        public Particle createParticle(TemplateParticleType templateParticleType, ClientWorld clientWorld, double d, double e, double f, double g, double h, double i) {
            var particle = new SpellFlameParticle(clientWorld, d, e, f, g, h, i);
            particle.setSprite(this.spriteProvider);

            particle.setColor(1F, 1F, 1F);
            TemplateParticleType.apply(templateParticleType, particle);
            float j = clientWorld.random.nextFloat() * 0.5F + 0.35F;
            particle.setColor(particle.red * j, particle.green * j, particle.blue * j);

            particle.spriteProviderForAnimation = this.spriteProvider;
            particle.velocityMultiplier = 0.8F;
            particle.alpha *= 0.8F;
            particle.glow = false;
            particle.gravityStrength = -0.01F;
            return particle;
        }
    }

    @Environment(EnvType.CLIENT)
    public static class FrostShard implements ParticleFactory<SimpleParticleType> {
        private final SpriteProvider spriteProvider;

        public FrostShard(SpriteProvider spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        public static Color color = Color.FROST;

        public Particle createParticle(SimpleParticleType SimpleParticleType, ClientWorld clientWorld, double d, double e, double f, double g, double h, double i) {
            var particle = new SpellFlameParticle(clientWorld, d, e, f, g, h, i);
            particle.setSprite(this.spriteProvider);
            float j = clientWorld.random.nextFloat() * 0.5F + 0.35F;
            particle.setColor(color.red() * j, color.green() * j, color.blue() * j);
            particle.velocityY *= clientWorld.random.nextFloat() * 0.2F + 0.9F;
            particle.maxAge = Math.round(clientWorld.random.nextFloat() * 3) + 5;
            return particle;
        }
    }

    @Environment(EnvType.CLIENT)
    public static class SignFactory implements ParticleFactory<TemplateParticleType> {
        private final SpriteProvider spriteProvider;
        private final SpellEngineParticles.Texture texture;

        public SignFactory(SpriteProvider spriteProvider, SpellEngineParticles.Texture texture) {
            this.spriteProvider = spriteProvider;
            this.texture = texture;
        }

        @Override
        public @Nullable Particle createParticle(TemplateParticleType particleType, ClientWorld clientWorld, double d, double e, double f, double g, double h, double i) {
            var particle = new SpellFlameParticle(clientWorld, d, e, f, g, h, i);
            particle.setSprite(this.spriteProvider);
            particle.velocityMultiplier = 0.7F;
            particle.scale = 0.4F;
            particle.alpha = 0.9F;
            particle.translucent = true;
            particle.setColor(1F, 1F, 1F);
            particle.maxAge = texture.frames() > 1 ? texture.frames() : 40;

            particle.applyAppearance(particleType.getAppearance());
            return particle;
        }
    }

}