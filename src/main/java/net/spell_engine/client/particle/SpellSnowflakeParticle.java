package net.spell_engine.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.util.math.Box;
import net.spell_engine.client.util.Color;
import net.spell_power.api.MagicSchool;

@Environment(value= EnvType.CLIENT)
public class SpellSnowflakeParticle extends SnowflakeParticle {
    boolean glow = true;
    protected SpellSnowflakeParticle(ClientWorld world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, SpriteProvider spriteProvider) {
        super(world, x, y, z, velocityX, velocityY, velocityZ, spriteProvider);
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public int getBrightness(float tint) {
        if (glow) {
            return 255;
        } else {
            return super.getBrightness(tint);
        }
    }

    @Environment(value=EnvType.CLIENT)
    public static class FrostFactory implements ParticleFactory<DefaultParticleType> {
        private final SpriteProvider spriteProvider;

        public FrostFactory(SpriteProvider spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        public static Color color = Color.from(MagicSchool.FROST.color());

        @Override
        public Particle createParticle(DefaultParticleType defaultParticleType, ClientWorld clientWorld, double d, double e, double f, double g, double h, double i) {
            var particle = new SpellSnowflakeParticle(clientWorld, d, e, f, g, h, i, this.spriteProvider);
            particle.setColor(color.red(), color.green(), color.red());
            particle.alpha = 0.75F;
            return particle;
        }
    }

    @Environment(value=EnvType.CLIENT)
    public static class HolyFactory implements ParticleFactory<DefaultParticleType> {
        private final SpriteProvider spriteProvider;

        public HolyFactory(SpriteProvider spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        public static Color color = Color.from(0xffffcc);

        @Override
        public Particle createParticle(DefaultParticleType defaultParticleType, ClientWorld clientWorld, double d, double e, double f, double g, double h, double i) {
            var particle = new SpellSnowflakeParticle(clientWorld, d, e, f, g, h, i, this.spriteProvider);
            particle.setColor(color.red(), color.green(), color.red());
            particle.alpha = 0.75F;
            return particle;
        }
    }

    @Environment(value=EnvType.CLIENT)
    public static class DrippingBloodFactory implements ParticleFactory<DefaultParticleType> {
        private final SpriteProvider spriteProvider;

        public DrippingBloodFactory(SpriteProvider spriteProvider) {
            this.spriteProvider = spriteProvider;
        }
        public static Color color = Color.from(0xb30000);

        @Override
        public Particle createParticle(DefaultParticleType defaultParticleType, ClientWorld clientWorld, double d, double e, double f, double g, double h, double i) {
            var particle = new SpellSnowflakeParticle(clientWorld, d, e, f, g, h, i, this.spriteProvider);
            particle.setColor(0.35F, 0, 0);
            particle.alpha = 1F;
            particle.glow = false;
            particle.velocityX *= 0.4;
            particle.velocityZ *= 0.4;
            particle.gravityStrength = 0.8F;
            return particle;
        }
    }

    @Environment(value=EnvType.CLIENT)
    public static class RootsFactory implements ParticleFactory<DefaultParticleType> {
        private final SpriteProvider spriteProvider;

        public RootsFactory(SpriteProvider spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        public static Color color = Color.from(MagicSchool.FROST.color());

        @Override
        public Particle createParticle(DefaultParticleType defaultParticleType, ClientWorld clientWorld, double d, double e, double f, double g, double h, double i) {
            var particle = new SpellSnowflakeParticle(clientWorld, d, e, f, g, h, i, this.spriteProvider);
            particle.setColor(1, 1, 1);
            particle.alpha = 1F;
            particle.glow = false;
            particle.setBoundingBoxSpacing(3f, 3f);
            particle.velocityX = 0;
            particle.velocityZ = 0;
            particle.scale = 0.25F;
            return particle;
        }
    }
}