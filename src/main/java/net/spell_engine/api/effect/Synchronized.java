package net.spell_engine.api.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;

import java.util.List;

public interface Synchronized {
    boolean shouldSynchronize();
    StatusEffect setSynchronized(boolean value);

    static void configure(StatusEffect effect, boolean isSynchronized) {
        ((Synchronized)effect).setSynchronized(isSynchronized);
    }

    record Effect(StatusEffect effect, int amplifier) { }
    static List<Effect> effectsOf(LivingEntity entity) {
        return ((Provider)entity).SpellEngine_syncedStatusEffects();
    }

    public interface Provider {
        List<Effect> SpellEngine_syncedStatusEffects();
    }
}
