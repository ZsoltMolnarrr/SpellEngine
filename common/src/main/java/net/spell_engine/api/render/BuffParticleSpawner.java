package net.spell_engine.api.render;

import net.minecraft.entity.LivingEntity;
import net.spell_engine.api.effect.CustomParticleStatusEffect;
import net.spell_engine.api.spell.fx.ParticleGroupEffect;
import net.spell_engine.client.util.Color;
import net.spell_engine.fx.ParticleHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class BuffParticleSpawner implements CustomParticleStatusEffect.Spawner {
    private final List<ParticleGroupEffect> particles;
    @Nullable private ParticleGroupEffect groundEffect;
    private int groundFrequency = 0;
    private int frequency = 0;
    private boolean invertedFrequency = false;
    private boolean scaleWithAmplifier = true;

    public static ParticleGroupEffect defaultBatch(String particleId, float particleCount) {
        return defaultBatch(particleId, particleCount, 0);
    }

    public static ParticleGroupEffect defaultBatch(String particleId, float particleCount, long color) {
        return defaultBatch(particleId, particleCount, 0.11F, 0.12F, color);
    }

    public static ParticleGroupEffect defaultBatch(String particleId, float particleCount, float min_speed, float max_speed) {
        return defaultBatch(particleId, particleCount, min_speed, max_speed, 0);
    }

    public static ParticleGroupEffect defaultBatch(String particleId, float particleCount, float min_speed, float max_speed, long color) {
        var effect = ParticleGroupEffect.of(particleId)
                .batch(b -> b.shape(ParticleGroupEffect.Shape.PIPE).widthFactor(2F)
                        .verticalOrigin(0.1F)
                        .count(particleCount).speed(min_speed, max_speed)
                        .extent(-0.2F));
        if (color != 0) {
            effect.particle.color = color;
        }
        return effect;
    }

    public BuffParticleSpawner(List<String> particleIds, float particleCount, float min_speed, float max_speed) {
        var particles = new ArrayList<ParticleGroupEffect>(particleIds.size());
        for (var particleId : particleIds) {
            particles.add(defaultBatch(particleId, particleCount, min_speed, max_speed));
        }
        this.particles = particles;
    }

    public BuffParticleSpawner(String particleId, float particleCount, float min_speed, float max_speed) {
        this.particles = List.of(defaultBatch(particleId, particleCount, min_speed, max_speed));
    }

    public BuffParticleSpawner(String particleId, float particleCount) {
        this(particleId, particleCount, 0.11F, 0.12F);
    }

    public BuffParticleSpawner(ParticleGroupEffect... particles) {
        this.particles = List.of(particles);
    }

    public BuffParticleSpawner withGroundEffect(String particleId, Color color, int frequency) {
        this.groundFrequency = frequency;
        this.groundEffect = ParticleGroupEffect.of(particleId)
                .particle(p -> p.color(color.toRGBA())
                        .attachment(ParticleGroupEffect.Attachment.POSITION))
                .batch(b -> b.shape(ParticleGroupEffect.Shape.SPHERE)
                        .anchor(ParticleGroupEffect.Anchor.GROUND));
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
            var scale = this.scaleWithAmplifier ? (amplifier + 1) : 1;
            List<ParticleGroupEffect> scaledParticles;
            if (scale == 1) {
                scaledParticles = particles;
            } else {
                var copies = new ArrayList<ParticleGroupEffect>(particles.size());
                for (var effect : particles) {
                    var copy = effect.copy();
                    copy.batch.count *= scale;
                    copies.add(copy);
                }
                scaledParticles = copies;
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
