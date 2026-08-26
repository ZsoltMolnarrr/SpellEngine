package net.spell_engine.utils;


import java.util.List;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

public class StatusEffectUtil {
    public record Diff(MobEffectInstance effect, int newAmplifier, int delay) {
        public Diff(MobEffectInstance effect, int newAmplifier) {
            this(effect, newAmplifier, 0);
        }
    }
    public static void applyChanges(LivingEntity livingEntity, List<Diff> changes) {
        for (var change : changes) {
            final var newAmplifier = change.newAmplifier;
            if (change.delay > 0) {
                final var effectType = change.effect.getEffect();
                ((WorldScheduler)livingEntity.level()).schedule(change.delay - 1, () -> {
                    var effect = livingEntity.getEffect(effectType);
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

    private static void processRemoval(LivingEntity livingEntity, MobEffectInstance effect, int newAmplifier) {
        if (newAmplifier < 0) {
            livingEntity.removeEffect(effect.getEffect());
        } else {
            var current = effect;
            var newInstance = copyWithNewAmplifier(current, newAmplifier);
            livingEntity.removeEffect(effect.getEffect());
            livingEntity.addEffect(newInstance);
        }
    }

    public static MobEffectInstance copyWithNewAmplifier(MobEffectInstance instance, int newAmplifier) {
        return new MobEffectInstance(
                instance.getEffect(), instance.getDuration(), newAmplifier, instance.isAmbient(),
                instance.isVisible(), instance.showIcon());
    }
}
