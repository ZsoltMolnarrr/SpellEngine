package net.spell_engine.api.render;

import net.minecraft.entity.LivingEntity;
import net.spell_engine.api.effect.CustomParticleStatusEffect;
import net.spell_engine.api.spell.fx.ParticleGroupBuilder;
import net.spell_engine.api.spell.fx.ParticleGroup;
import net.spell_engine.client.util.Color;
import net.spell_engine.fx.ParticleHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class BuffParticleSpawner implements CustomParticleStatusEffect.Spawner {
    private final List<ParticleGroup> particles;
    @Nullable private ParticleGroup groundEffect;
    private int groundFrequency = 0;
    private int frequency = 0;
    private boolean invertedFrequency = false;
    private boolean scaleWithAmplifier = true;
    private Spacing spacing = Spacing.RANDOM;

    /// How a density below one particle per tick is realised.
    ///
    /// Amplifier scaling multiplies the density either way, so this only decides what a
    /// leftover fraction looks like: `0.25` is one particle every fourth tick under
    /// [#EVEN], or a quarter chance on each tick under [#RANDOM].
    public enum Spacing {
        /// Regular — the particle lands on every Nth tick. Reads as a steady pulse.
        EVEN,
        /// Random — each tick rolls independently. Reads as a scatter, and is how buff
        /// particles behaved before 1.10, so it stays the default here.
        RANDOM
    }

    public static ParticleGroup defaultBatch(String particleId, float particleCount) {
        return defaultBatch(particleId, particleCount, 0);
    }

    public static ParticleGroup defaultBatch(String particleId, float particleCount, long color) {
        return defaultBatch(particleId, particleCount, 0.11F, 0.12F, color);
    }

    public static ParticleGroup defaultBatch(String particleId, float particleCount, float min_speed, float max_speed) {
        return defaultBatch(particleId, particleCount, min_speed, max_speed, 0);
    }

    public static ParticleGroup defaultBatch(String particleId, float particleCount, float min_speed, float max_speed, long color) {
        var builder = ParticleGroupBuilder.of(particleId);
        if (color != 0) {
            builder.color(color);
        }
        return builder.batch(ParticleGroupBuilder.Batches.casting(particleCount, max_speed)
                .andThen(b -> b.speed(min_speed, max_speed).extent(-0.2F)));
    }

    public BuffParticleSpawner(List<String> particleIds, float particleCount, float min_speed, float max_speed) {
        var particles = new ArrayList<ParticleGroup>(particleIds.size());
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

    public BuffParticleSpawner(ParticleGroup... particles) {
        this.particles = List.of(particles);
    }

    public BuffParticleSpawner withGroundEffect(String particleId, Color color, int frequency) {
        this.groundFrequency = frequency;
        this.groundEffect = ParticleGroupBuilder.of(particleId)
                .color(color).attached()
                .batch(ParticleGroupBuilder.Batches.ground(1));
        return this;
    }

    /// See [Spacing]. Defaults to [Spacing#RANDOM].
    public BuffParticleSpawner spacing(Spacing spacing) {
        this.spacing = spacing;
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

    private boolean hasFractionalCount(int scale) {
        for (var effect : particles) {
            var scaled = effect.batch.count * scale;
            if (scaled > 0F && scaled < 1F) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void spawnParticles(LivingEntity livingEntity, int amplifier) {
        var time = livingEntity.age;
        var spawn = frequency == 0
                || (!invertedFrequency ? (time % frequency == 0) : (time % (frequency / (amplifier + 1)) == 0));
        if (spawn) {
            var scale = this.scaleWithAmplifier ? (amplifier + 1) : 1;
            var needsRoll = spacing == Spacing.RANDOM && hasFractionalCount(scale);
            List<ParticleGroup> scaledParticles;
            if (scale == 1 && !needsRoll) {
                scaledParticles = particles;
            } else {
                var copies = new ArrayList<ParticleGroup>(particles.size());
                for (var effect : particles) {
                    var copy = effect.copy();
                    copy.batch.count *= scale;
                    // Scale first, then decide what any leftover fraction means: a period
                    // (EVEN) or an independent roll per tick (RANDOM).
                    if (spacing == Spacing.RANDOM && copy.batch.count > 0F && copy.batch.count < 1F) {
                        copy.batch.chance *= copy.batch.count;
                        copy.batch.count = 1F;
                    }
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
