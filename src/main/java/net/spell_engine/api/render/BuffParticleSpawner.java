package net.spell_engine.api.render;

import net.minecraft.entity.LivingEntity;
import net.spell_engine.api.effect.CustomParticleStatusEffect;
import net.spell_engine.api.spell.fx.ParticleBatch;
import net.spell_engine.client.util.Color;
import net.spell_engine.fx.ParticleHelper;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class BuffParticleSpawner implements CustomParticleStatusEffect.Spawner {
    private final ParticleBatch[] particles;
    @Nullable private ParticleBatch groundEffect;
    private int groundFrequency = 0;
    private int frequency = 0;
    private boolean invertedFrequency = false;
    private boolean scaleWithAmplifier = true;

    public static ParticleBatch defaultBatch(String particleId, float particleCount) {
        return defaultBatch(particleId, particleCount, 0);
    }

    public static ParticleBatch defaultBatch(String particleId, float particleCount, long color) {
        return defaultBatch(particleId, particleCount, 0.11F, 0.12F, color);
    }

    public static ParticleBatch defaultBatch(String particleId, float particleCount, float min_speed, float max_speed) {
        return defaultBatch(particleId, particleCount, min_speed, max_speed, 0);
    }

    public static ParticleBatch defaultBatch(String particleId, float particleCount, float min_speed, float max_speed, long color) {
        var batch = new ParticleBatch(
                particleId,
                ParticleBatch.Shape.WIDE_PIPE,
                ParticleBatch.Origin.FEET,
                null,
                particleCount,
                min_speed,
                max_speed,
                0,
                -0.2F);
        if (color != 0) {
            batch.color(color);
        }
        return batch;
    }

    public BuffParticleSpawner(List<String> particleIds, float particleCount, float min_speed, float max_speed) {
        this.particles = new ParticleBatch[particleIds.size()];
        for (int i = 0; i < particleIds.size(); i++) {
            particles[i] = defaultBatch(particleIds.get(i), particleCount, min_speed, max_speed);
        }
    }

    public BuffParticleSpawner(String particleId, float particleCount, float min_speed, float max_speed) {
        this.particles = new ParticleBatch[] { defaultBatch(particleId, particleCount, min_speed, max_speed) };
    }

    public BuffParticleSpawner(String particleId, float particleCount) {
        this(particleId, particleCount, 0.11F, 0.12F);
    }

    public BuffParticleSpawner(ParticleBatch particleBatch) {
        this(new ParticleBatch[] { particleBatch });
    }

    public BuffParticleSpawner(ParticleBatch[] particles) {
        this.particles = particles;
    }

    public BuffParticleSpawner withGroundEffect(String particleId, Color color, int frequency) {
        this.groundFrequency = frequency;
        this.groundEffect = new ParticleBatch(particleId.toString(),
                ParticleBatch.Shape.SPHERE, ParticleBatch.Origin.GROUND,
                1, 0.0F, 0.F)
                .color(color.toRGBA())
                .followEntity(true);
        return this;
    }

    public BuffParticleSpawner scaleWithAmplifier(boolean scaleWithAmplifier) {
        this.scaleWithAmplifier = scaleWithAmplifier;
        return this;
    }

    public BuffParticleSpawner withFrequency(int frequency) {
        this.frequency = frequency;
        return this;
    }

    public BuffParticleSpawner invertFrequency() {
        this.invertedFrequency = true;
        return this;
    }

    @Override
    public void spawnParticles(LivingEntity livingEntity, int amplifier) {
        var time = livingEntity.age;
        var spawn = frequency == 0
                || (!invertedFrequency ? (time % frequency == 0) : (time % (frequency / (amplifier + 1)) == 0));
        if (spawn) {
            var scaledParticles = new ParticleBatch[particles.length];
            var scale = this.scaleWithAmplifier ? (amplifier + 1) : 1;
            for (int i = 0; i < particles.length; i++) {
                var copiedBatch = new ParticleBatch(particles[i]);
                copiedBatch.count *= scale;
                scaledParticles[i] = copiedBatch;
            }
            ParticleHelper.play(livingEntity.getWorld(), livingEntity, scaledParticles);
        }
        if (groundEffect != null && groundFrequency > 0) {
            if (livingEntity.age % groundFrequency == 0) {
                ParticleHelper.play(livingEntity.getWorld(), livingEntity, groundEffect);
            }
        }
    }
}