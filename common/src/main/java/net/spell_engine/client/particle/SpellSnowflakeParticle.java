package net.spell_engine.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DefaultParticleType;
import net.spell_damage.api.MagicSchool;
import net.spell_engine.client.util.Color;

@Environment(value= EnvType.CLIENT)
public class SpellSnowflakeParticle extends SnowflakeParticle {
    protected SpellSnowflakeParticle(ClientWorld world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, SpriteProvider spriteProvider) {
        super(world, x, y, z, velocityX, velocityY, velocityZ, spriteProvider);
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public int getBrightness(float tint) {
        return 255;
    }

    @Environment(value=EnvType.CLIENT)
    public static class Factory implements ParticleFactory<DefaultParticleType> {
        private final SpriteProvider spriteProvider;

        public Factory(SpriteProvider spriteProvider) {
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
}