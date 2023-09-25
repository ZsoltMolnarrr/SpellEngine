package net.spell_engine.mixin.client;

import net.minecraft.entity.LivingEntity;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.effect.CustomParticleStatusEffect;
import net.spell_engine.api.effect.Synchronized;
import net.spell_engine.client.beam.BeamEmitterEntity;
import net.spell_engine.internals.Beam;
import net.spell_engine.internals.casting.SpellCasterEntity;
import net.spell_engine.particle.ParticleHelper;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class LivingEntityVisualMixin implements BeamEmitterEntity {
    private LivingEntity livingEntity() {
        return (LivingEntity) ((Object) this);
    }

    @Nullable
    public Beam.Rendered lastRenderedBeam;

    @Override
    public void setLastRenderedBeam(Beam.Rendered beam) {
        lastRenderedBeam = beam;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void tick_TAIL_spawnBeamParticles(CallbackInfo ci) {
        var livingEntity = livingEntity();
        Spell.Release.Target.Beam beam = null;
        if (livingEntity instanceof SpellCasterEntity caster) {
            beam = caster.getBeam();
        }
        var renderedBeam = lastRenderedBeam;
        if (livingEntity.getWorld().isClient && beam != null && renderedBeam != null) {
            var position = renderedBeam.position();
            var appearance = renderedBeam.appearance();

            var yaw = livingEntity.getYaw();

            if (position.hitBlock()) {
                for (var batch : appearance.block_hit_particles) {
                    ParticleHelper.play(livingEntity.getWorld(), position.end(),
                            appearance.width * 2, yaw, livingEntity.getPitch(), batch);
                }
            }
        }
    }

    @Inject(method = "tickStatusEffects", at = @At("TAIL"))
    private void tickStatusEffects_TAIL_SpellEngine_CustomParticles(CallbackInfo ci) {
        var livingEntity = livingEntity();
        if (!livingEntity.isAlive() || !livingEntity.getWorld().isClient()) {
            return;
        }

        for (var entry: Synchronized.effectsOf(livingEntity)) {
            var effect = entry.effect();
            var amplifier = entry.amplifier();
            var spawner = CustomParticleStatusEffect.spawnerOf(effect);
            if (spawner != null) {
                spawner.spawnParticles(livingEntity, amplifier);
            }
        }
    }
}