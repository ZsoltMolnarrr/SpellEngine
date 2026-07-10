package net.spell_engine.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.ExplosionLargeParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.spell_engine.client.util.Color;
import org.jetbrains.annotations.Nullable;

public class SpellExplosionParticle extends ExplosionLargeParticle implements AppearanceAware {
    protected SpellExplosionParticle(ClientWorld world, double x, double y, double z, double d, SpriteProvider spriteProvider) {
        super(world, x, y, z, d, spriteProvider);
    }

    @Override
    public int getBrightness(float tint) {
        return 255;
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
        // ExplosionLargeParticle has no entity following support
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
            particle.setColor(1F, 1F, 1F);
            particle.maxAge = 10;

            particle.applyAppearance(particleType.getAppearance());
            return particle;
        }
    }
}
