package net.spell_engine.utils;


import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;

import java.util.List;

public class StatusEffectUtil {
    public record Diff(StatusEffectInstance effect, int newAmplifier, int delay) {
        public Diff(StatusEffectInstance effect, int newAmplifier) {
            this(effect, newAmplifier, 0);
        }
    }
    public static void applyChanges(LivingEntity livingEntity, List<Diff> changes) {
        for (var change : changes) {
            final var newAmplifier = change.newAmplifier;
            if (change.delay > 0) {
                final var effectType = change.effect.getEffectType();
                ((WorldScheduler)livingEntity.getWorld()).schedule(change.delay - 1, () -> {
                    var effect = livingEntity.getStatusEffect(effectType);
                    if (effect == null) {
                        // If the effect is not present, we can skip processing
                        return;
                    }
                    processRemoval(livingEntity, effect, newAmplifier);
                });
            } else {
                processRemoval(livingEntity, change.effect, newAmplifier);
            }
        }
    }

    private static void processRemoval(LivingEntity livingEntity, StatusEffectInstance effect, int newAmplifier) {
        if (newAmplifier < 0) {
            livingEntity.removeStatusEffect(effect.getEffectType());
        } else {
            var current = effect;
            var newInstance = copyWithNewAmplifier(current, newAmplifier);
            livingEntity.removeStatusEffect(effect.getEffectType());
            livingEntity.addStatusEffect(newInstance);
        }
    }

    public static StatusEffectInstance copyWithNewAmplifier(StatusEffectInstance instance, int newAmplifier) {
        return new StatusEffectInstance(
                instance.getEffectType(), instance.getDuration(), newAmplifier, instance.isAmbient(),
                instance.shouldShowParticles(), instance.shouldShowIcon());
    }
}
