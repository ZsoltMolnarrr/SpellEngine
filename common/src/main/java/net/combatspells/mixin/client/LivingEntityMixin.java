package net.combatspells.mixin.client;

import net.combatspells.api.spell.Spell;
import net.combatspells.client.CombatSpellsClient;
import net.combatspells.client.beam.BeamEmitterEntity;
import net.combatspells.internals.Beam;
import net.combatspells.internals.SpellCasterEntity;
import net.combatspells.utils.ParticleHelper;
import net.combatspells.utils.TargetHelper;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class LivingEntityMixin implements BeamEmitterEntity {
    private LivingEntity livingEntity() {
        return (LivingEntity) ((Object)this);
    }

    @Inject(method = "isGlowing", at = @At("HEAD"), cancellable = true)
    public void isGlowing_HEAD(CallbackInfoReturnable<Boolean> cir) {
        if (TargetHelper.isTargetedByClientPlayer(livingEntity()) && CombatSpellsClient.config.highlightTarget) {
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
    public void tick_TAIL_spawnBeamParticles(CallbackInfo ci) {
        var livingEntity = livingEntity();
        Spell.Release.Target.Beam beam = null;
        if (livingEntity instanceof SpellCasterEntity caster) {
            beam = caster.getBeam();
        }
        var renderedBeam = lastRenderedBeam;
        if (livingEntity.world.isClient && beam != null && renderedBeam != null) {
            var position = renderedBeam.position();
            var appearance = renderedBeam.appearance();

            for (var batch: appearance.emit_particles) {
                ParticleHelper.play(livingEntity.world, position.origin(),
                appearance.width * 2, livingEntity.getYaw(), livingEntity.getPitch() + 90, batch);
            }

            if (position.hitBlock()) {
                for (var batch: appearance.block_hit_particles) {
                    ParticleHelper.play(livingEntity.world, position.end(),
                            appearance.width * 2, livingEntity.getYaw(), livingEntity.getPitch() + 90, batch);
                }
            }
        }
    }
}