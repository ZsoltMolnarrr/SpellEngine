package net.spell_engine.api.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.sound.SoundEvent;
import net.spell_engine.api.spell.fx.ParticleGroup;
import net.spell_engine.fx.ParticleHelper;
import net.spell_engine.utils.SoundHelper;
import net.spell_engine.utils.StatusEffectUtil;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DISCLAIMER: This API only works on PlayerEntities (due to performance)
 */
public class Protection {
    public record Pop(List<ParticleGroup> particles, @Nullable SoundEvent sound) { }
    /// 1.20.1: status effects are plain registry objects, so `effect` is the raw `StatusEffect`.
    public record Entry(StatusEffect effect, TagKey<DamageType> protects,
                        int decrement, Pop onDecrement, Pop onRemove) { }
    public static final Map<StatusEffect, Entry> PROTECTIONS = new HashMap<>();

    public static void register(StatusEffect effect, Pop pop) {
        register(effect, new Entry(effect, null, 1, pop, pop));
    }

    public static void register(StatusEffect effect, TagKey<DamageType> protects, Pop pop) {
        register(effect, new Entry(effect, protects, 1, pop, pop));
    }

    public static void register(RegistryEntry<StatusEffect> effectEntry, Pop pop) {
        register(effectEntry.value(), pop);
    }

    public static void register(RegistryEntry<StatusEffect> effectEntry, TagKey<DamageType> protects, Pop pop) {
        register(effectEntry.value(), protects, pop);
    }

    public static void register(StatusEffect effect, Entry entry) {
        PROTECTIONS.put(effect, entry);
    }

    public static boolean tryProtect(LivingEntity entity, DamageSource damageSource) {
        for (var entry: entity.getActiveStatusEffects().entrySet()) {
            var protection = PROTECTIONS.get(entry.getKey());
            if (protection != null) {
                if (protection.protects != null && !damageSource.isIn(protection.protects)) {
                    continue; // This protection does not apply to this damage type
                }
                var effect = entry.getValue();
                var newAmplifier = effect.getAmplifier() - protection.decrement;

                var pop = newAmplifier < 0 ? protection.onRemove : protection.onDecrement;
                if (pop != null) {
                    ParticleHelper.sendBatches(entity, pop.particles);
                    if (pop.sound != null) {
                        SoundHelper.playSoundEvent(entity.getWorld(), entity, pop.sound);
                    }
                }
                StatusEffectUtil.applyChanges(entity, List.of(
                        new StatusEffectUtil.Diff(effect, newAmplifier)
                ));
                return true;
            }
        }
        return false;
    }
}
