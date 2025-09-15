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
import net.spell_power.api.SpellSchools;
import org.jetbrains.annotations.Nullable;

@Environment(value= EnvType.CLIENT)
public class SpellFlameParticle extends AbstractSlowingParticle {
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

    @Environment(EnvType.CLIENT)
    public static class FlameFactory implements ParticleFactory<SimpleParticleType> {
        private final SpriteProvider spriteProvider;

        public FlameFactory(SpriteProvider spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        public Particle createParticle(SimpleParticleType SimpleParticleType, ClientWorld clientWorld, double d, double e, double f, double g, double h, double i) {
            var particle = new SpellFlameParticle(clientWorld, d, e, f, g, h, i);
            particle.setSprite(this.spriteProvider);
            return particle;
        }
    }

    @Environment(EnvType.CLIENT)
    public static class AnimatedFlameFactory implements ParticleFactory<SimpleParticleType> {
        private final SpriteProvider spriteProvider;

        public AnimatedFlameFactory(SpriteProvider spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        public Particle createParticle(SimpleParticleType SimpleParticleType, ClientWorld clientWorld, double d, double e, double f, double g, double h, double i) {
            var particle = new SpellFlameParticle(clientWorld, d, e, f, g, h, i);
            particle.setSprite(this.spriteProvider);
            particle.spriteProviderForAnimation = this.spriteProvider;
            return particle;
        }
    }

    @Environment(EnvType.CLIENT)
    public static class ColoredAnimatedFactory implements ParticleFactory<SimpleParticleType> {
        private final SpriteProvider spriteProvider;
        private final Color color;
        private final float scale;
        protected float randomColorFloor = 0.5F;
        protected float randomColorRange = 0.35F;

        public ColoredAnimatedFactory(Color color, float scale, SpriteProvider spriteProvider) {
            this.spriteProvider = spriteProvider;
            this.color = color;
            this.scale = scale;
        }

        public Particle createParticle(SimpleParticleType SimpleParticleType, ClientWorld clientWorld, double d, double e, double f, double g, double h, double i) {
            var particle = new SpellFlameParticle(clientWorld, d, e, f, g, h, i);
            particle.setSprite(this.spriteProvider);
            particle.spriteProviderForAnimation = this.spriteProvider;

            var red = color.red();
            var green = color.green();
            var blue = color.blue();
            if (randomColorRange > 0) {
                red = (clientWorld.random.nextFloat() * randomColorFloor + randomColorRange) * red;
                green = (clientWorld.random.nextFloat() * randomColorFloor + randomColorRange) * green;
                blue = (clientWorld.random.nextFloat() * randomColorFloor + randomColorRange) * blue;
            }
            float j = clientWorld.random.nextFloat() * 0.5F + 0.35F;
            particle.setColor(red, green, blue);
            particle.scale = this.scale;
            particle.setAlpha(1F);
            return particle;
        }
    }

    @Environment(EnvType.CLIENT)
    public static class ElectricSparkFactory extends ColoredAnimatedFactory {
        public ElectricSparkFactory(SpriteProvider spriteProvider) {
            super(Color.ELECTRIC, 0.75F, spriteProvider);
            randomColorRange = 0F;
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
    public static class WeaknessSmokeFactory implements ParticleFactory<SimpleParticleType> {
        private final SpriteProvider spriteProvider;
        public Color color = Color.from(0x993333);
        public WeaknessSmokeFactory(SpriteProvider spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        public Particle createParticle(SimpleParticleType SimpleParticleType, ClientWorld clientWorld, double d, double e, double f, double g, double h, double i) {
            var particle = new SpellFlameParticle(clientWorld, d, e, f, g, h, i);
            particle.setSprite(this.spriteProvider);
            float j = clientWorld.random.nextFloat() * 0.5F + 0.35F;
            particle.setColor(color.red() * j, color.green() * j, color.blue() * j);
            particle.spriteProviderForAnimation = this.spriteProvider;
            particle.velocityMultiplier = 0.8F;
            particle.setAlpha(0.7F);
            particle.glow = false;
            particle.gravityStrength = 0.01F;
            return particle;
        }
    }

    @Environment(EnvType.CLIENT)
    public static class MediumFlameFactory implements ParticleFactory<SimpleParticleType> {
        private final SpriteProvider spriteProvider;

        public MediumFlameFactory(SpriteProvider spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        public Particle createParticle(SimpleParticleType SimpleParticleType, ClientWorld clientWorld, double d, double e, double f, double g, double h, double i) {
            var particle = new SpellFlameParticle(clientWorld, d, e, f, g, h, i);
            particle.setSprite(this.spriteProvider);
            particle.spriteProviderForAnimation = this.spriteProvider;
            particle.scale = 0.5F;
            particle.maxAge *= 0.5;
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

    public static class ColorableFactory implements ParticleFactory<SimpleParticleType> {
        private final SpriteProvider spriteProvider;
        public Color color = Color.from(0xffffff);

        public ColorableFactory(SpriteProvider spriteProvider, Color color) {
            this.spriteProvider = spriteProvider;
            this.color = color;
        }

        @Nullable
        @Override
        public Particle createParticle(SimpleParticleType SimpleParticleType, ClientWorld clientWorld, double d, double e, double f, double g, double h, double i) {
            var particle = new SpellFlameParticle(clientWorld, d, e, f, g, h, i);
            particle.setSprite(this.spriteProvider);
            float j = clientWorld.random.nextFloat() * 0.5F + 0.35F;
            particle.setColor(color.red() * j, color.green() * j, color.blue() * j);
            return particle;
        }
    }


    @Environment(EnvType.CLIENT)
    public static class HealingFactory extends ColorableFactory {
        public HealingFactory(SpriteProvider spriteProvider) {
            super(spriteProvider, Color.from(SpellSchools.HEALING.color));
        }
    }

    @Environment(EnvType.CLIENT)
    public static class HolyFactory extends ColorableFactory {
        public HolyFactory(SpriteProvider spriteProvider) {
            super(spriteProvider, Color.HOLY);
        }
    }

    @Environment(EnvType.CLIENT)
    public static class NatureFactory extends ColorableFactory {
        public NatureFactory(SpriteProvider spriteProvider) {
            super(spriteProvider, Color.NATURE);
        }
    }

    @Environment(EnvType.CLIENT)
    public static class WhiteFactory extends ColorableFactory {
        public WhiteFactory(SpriteProvider spriteProvider) {
            super(spriteProvider, Color.WHITE);
        }
    }

    @Environment(EnvType.CLIENT)
    public static class NatureSlowingFactory extends NatureFactory {
        public NatureSlowingFactory(SpriteProvider spriteProvider) {
            super(spriteProvider);
        }
        @Override
        public @Nullable Particle createParticle(SimpleParticleType SimpleParticleType, ClientWorld clientWorld, double d, double e, double f, double g, double h, double i) {
            var particle = (SpellFlameParticle)super.createParticle(SimpleParticleType, clientWorld, d, e, f, g, h, i);
            particle.velocityMultiplier = 0.8F;
            return particle;
        }
    }

    @Environment(EnvType.CLIENT)
    public static class BuffFactory implements ParticleFactory<SimpleParticleType> {
        private final SpriteProvider spriteProvider;
        public Color color = Color.from(0xffffff);

        public BuffFactory(SpriteProvider spriteProvider, Color color) {
            this.spriteProvider = spriteProvider;
            this.color = color;
        }

        @Nullable
        @Override
        public Particle createParticle(SimpleParticleType SimpleParticleType, ClientWorld clientWorld, double d, double e, double f, double g, double h, double i) {
            var particle = new SpellFlameParticle(clientWorld, d, e, f, g, h, i);
            particle.setSprite(this.spriteProvider);
            float j = clientWorld.random.nextFloat() * 0.5F + 0.35F;
            particle.setColor(color.red() * j, color.green() * j, color.blue() * j);
            particle.maxAge = 16;
            particle.translucent = true;
            return particle;
        }
    }

    @Environment(EnvType.CLIENT)
    public static class BuffRageFactory extends BuffFactory {
        public BuffRageFactory(SpriteProvider spriteProvider) {
            super(spriteProvider, Color.RAGE);
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

            particle.red = 1F;
            particle.green = 1F;
            particle.blue = 1F;

            particle.maxAge = texture.frames() > 1 ? texture.frames() : 40;

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