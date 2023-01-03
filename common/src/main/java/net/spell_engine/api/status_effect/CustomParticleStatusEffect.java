package net.spell_engine.api.status_effect;

import net.minecraft.entity.LivingEntity;

public interface CustomParticleStatusEffect extends SynchronizedStatusEffect {
    void spawnParticles(LivingEntity livingEntity, int amplifier);
}
