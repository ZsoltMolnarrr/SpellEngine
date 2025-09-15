package net.spell_engine.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.*;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import org.joml.Quaternionf;

@Environment(value= EnvType.CLIENT)
public class ShiftedParticle
        extends SpriteBillboardParticle {
    private final SpriteProvider spriteProvider;

    protected ShiftedParticle(ClientWorld world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, SpriteProvider spriteProvider) {
        super(world, x, y, z);
        this.gravityStrength = 0.225f;
        this.velocityMultiplier = 1.0f;
        this.spriteProvider = spriteProvider;
        this.velocityY = velocityY + (Math.random() * 2.0 - 1.0) * (double) 0.05f;
        this.scale = 0.1f * (this.random.nextFloat() * this.random.nextFloat() * 1.0f + 1.0f);
        this.maxAge = (int) (16.0 / ((double) this.random.nextFloat() * 0.8 + 0.2)) + 2;
        this.setSpriteForAge(spriteProvider);
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_OPAQUE;
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteForAge(this.spriteProvider);
        this.velocityX *= (double) 0.95f;
        this.velocityY *= (double) 0.9f;
        this.velocityZ *= (double) 0.95f;
    }

    /**
     * Shift the particle on the Y axis
     */
    @Override
    protected void method_60374(VertexConsumer vertexConsumer, Quaternionf quaternionf, float f, float g, float h, float i) {
        var y = g + this.getSize(0);
        super.method_60374(vertexConsumer, quaternionf, f, y, h, i);
    }

    @Environment(value=EnvType.CLIENT)
    public static class RootsFactory implements ParticleFactory<SimpleParticleType> {
        private final SpriteProvider spriteProvider;

        public RootsFactory(SpriteProvider spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        @Override
        public Particle createParticle(SimpleParticleType SimpleParticleType, ClientWorld clientWorld, double d, double e, double f, double g, double h, double i) {
            var particle = new ShiftedParticle(clientWorld, d, e, f, g, h, i, this.spriteProvider);
            particle.setColor(1, 1, 1);
            particle.alpha = 1F;
            particle.setBoundingBoxSpacing(3f, 3f);
            particle.velocityX = 0;
            particle.velocityZ = 0;
            particle.scale = 0.25F;
            return particle;
        }
    }
}
