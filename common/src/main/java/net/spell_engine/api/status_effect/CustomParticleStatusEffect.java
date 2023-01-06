package net.spell_engine.api.status_effect;

import net.minecraft.entity.LivingEntity;

public interface CustomParticleStatusEffect {
    void spawnParticles(LivingEntity livingEntity, int amplifier);
}
