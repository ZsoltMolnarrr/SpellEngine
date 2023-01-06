package net.spell_engine.wizards;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.spell_engine.api.spell.ParticleBatch;
import net.spell_engine.api.status_effect.CustomParticleStatusEffect;
import net.spell_engine.particle.ParticleHelper;
import net.spell_power.api.statuseffects.SpellVulnerabilityStatusEffect;

public class FrozenStatusEffect extends SpellVulnerabilityStatusEffect
        implements CustomParticleStatusEffect {
    public FrozenStatusEffect(StatusEffectCategory statusEffectCategory, int color) {
        super(statusEffectCategory, color);
    }

    public static final ParticleBatch particles = new ParticleBatch(
            "spell_engine:frost_hit",
            ParticleBatch.Shape.SPHERE,
            ParticleBatch.Origin.CENTER,
            null,
            1,
            0.1F,
            0.3F,
            0);

    @Override
    public void spawnParticles(LivingEntity livingEntity, int amplifier) {
        var scaledParticles = new ParticleBatch(particles);
        scaledParticles.count *= (amplifier + 1);
        ParticleHelper.play(livingEntity.world, livingEntity, scaledParticles);
    }
}
