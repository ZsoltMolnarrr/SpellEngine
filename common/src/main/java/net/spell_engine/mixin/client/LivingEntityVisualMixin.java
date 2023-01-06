package net.spell_engine.mixin.client;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.util.registry.Registry;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.status_effect.CustomParticleStatusEffect;
import net.spell_engine.api.status_effect.Synchronized;
import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.client.beam.BeamEmitterEntity;
import net.spell_engine.internals.Beam;
import net.spell_engine.internals.SpellCasterEntity;
import net.spell_engine.particle.ParticleHelper;
import net.spell_engine.utils.TargetHelper;
import net.minecraft.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(LivingEntity.class)
public class LivingEntityVisualMixin implements BeamEmitterEntity {
    @Shadow @Final private Map<StatusEffect, StatusEffectInstance> activeStatusEffects;

    private LivingEntity livingEntity() {
        return (LivingEntity) ((Object) this);
    }

    @Inject(method = "isGlowing", at = @At("HEAD"), cancellable = true)
    public void isGlowing_HEAD(CallbackInfoReturnable<Boolean> cir) {
        if (TargetHelper.isTargetedByClientPlayer(livingEntity()) && SpellEngineClient.config.highlightTarget) {
            cir.setReturnValue(true);
            cir.cancel();
        }
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
        if (livingEntity.world.isClient && beam != null && renderedBeam != null) {
            var position = renderedBeam.position();
            var appearance = renderedBeam.appearance();

            var yaw = livingEntity.getYaw();

            if (position.hitBlock()) {
                for (var batch : appearance.block_hit_particles) {
                    ParticleHelper.play(livingEntity.world, position.end(),
                            appearance.width * 2, yaw, livingEntity.getPitch(), batch);
                }
            }
        }
    }

    @Inject(method = "tickStatusEffects", at = @At("TAIL"))
    private void tickStatusEffects_TAIL_SpellEngine_CustomParticles(CallbackInfo ci) {
        var livingEntity = livingEntity();
        if (!livingEntity.isAlive() || !livingEntity.world.isClient()) {
            return;
        }

        for (var entry: Synchronized.effectsOf(livingEntity)) {
            var effect = entry.effect();
            var amplifier = entry.amplifier();
            if (effect instanceof CustomParticleStatusEffect customEffect) {
                customEffect.spawnParticles(livingEntity, amplifier);
            }
        }
    }
}