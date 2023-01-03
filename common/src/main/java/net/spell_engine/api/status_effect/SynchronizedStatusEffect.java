package net.spell_engine.api.status_effect;

import net.minecraft.entity.LivingEntity;

import java.util.Map;

public interface SynchronizedStatusEffect {
    static Map<Integer, Integer> all(LivingEntity entity) {
        return ((Provider)entity).SpellEngine_syncedStatusEffects();
    }
    static Integer effectAmplifier(LivingEntity entity, int rawId) {
        return (((Provider)entity).SpellEngine_syncedStatusEffects()).get(rawId);
    }

    static boolean hasEffect(LivingEntity entity, int rawId) {
        return (((Provider)entity).SpellEngine_syncedStatusEffects()).containsKey(rawId);
    }

    interface Provider {
        Map<Integer, Integer> SpellEngine_syncedStatusEffects();
    }
}
