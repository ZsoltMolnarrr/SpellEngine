package net.spell_engine.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.ExplosionLargeParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;

public class SpellExplosionParticle extends ExplosionLargeParticle {
    protected SpellExplosionParticle(ClientWorld world, double x, double y, double z, double d, SpriteProvider spriteProvider) {
        super(world, x, y, z, d, spriteProvider);
    }

    @Override
    public int getBrightness(float tint) {
        return 255;
    }

    @Environment(EnvType.CLIENT)
    public static class Factory implements ParticleFactory<SimpleParticleType> {

        private final SpriteProvider spriteProvider;

        public Factory(SpriteProvider spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        public Particle createParticle(SimpleParticleType SimpleParticleType, ClientWorld clientWorld, double d, double e, double f, double g, double h, double i) {
            var particle = new SpellExplosionParticle(clientWorld, d, e, f, g, this.spriteProvider);
            particle.scale = 1.2F;
            particle.red = 1F;
            particle.green = 1F;
            particle.blue = 1F;
            particle.maxAge = 10;
            return particle;
        }
    }

    @Environment(EnvType.CLIENT)
    public static class TemplateFactory implements ParticleFactory<TemplateParticleType> {

        private final SpriteProvider spriteProvider;

        public TemplateFactory(SpriteProvider spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        public Particle createParticle(TemplateParticleType particleType, ClientWorld clientWorld, double d, double e, double f, double g, double h, double i) {
            var particle = new SpellExplosionParticle(clientWorld, d, e, f, g, this.spriteProvider);
            particle.scale = 1.2F;
            particle.red = 1F;
            particle.green = 1F;
            particle.blue = 1F;
            particle.maxAge = 10;

            TemplateParticleType.apply(particleType, particle);
            var appearance = particleType.getAppearance();
            if (appearance != null) {
                var color = appearance.color;
                if (color != null) {
                    particle.alpha *= appearance.color.alpha();
                }
                particle.scale *= appearance.scale;
                // particle.followEntity = appearance.entityFollowed;
            }

            return particle;
        }
    }
}
