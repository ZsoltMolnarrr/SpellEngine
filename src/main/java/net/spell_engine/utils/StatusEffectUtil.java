package net.spell_engine.utils;


import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;

import java.util.List;

public class StatusEffectUtil {
    public record Diff(StatusEffectInstance effect, int newAmplifier)  { }
    public static void applyChanges(LivingEntity livingEntity, List<Diff> changes) {
        for (var change : changes) {
            if (change.newAmplifier < 0) {
                livingEntity.removeStatusEffect(change.effect.getEffectType());
            } else {
                var current = change.effect;
                var newInstance = copyWithNewAmplifier(current, change.newAmplifier);
                livingEntity.removeStatusEffect(change.effect.getEffectType());
                livingEntity.addStatusEffect(newInstance);
            }
        }
    }

    public static StatusEffectInstance copyWithNewAmplifier(StatusEffectInstance instance, int newAmplifier) {
        return new StatusEffectInstance(
                instance.getEffectType(), instance.getDuration(), newAmplifier, instance.isAmbient(),
                instance.shouldShowIcon(), instance.shouldShowParticles());
    }
}
