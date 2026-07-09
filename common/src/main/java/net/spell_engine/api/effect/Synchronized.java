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

    /// `appliedAtWorldTime` is the server's `World.getTime()` at the moment the effect was applied.
    /// World time shares an origin and rate on both sides and is re-synced each second, so a client
    /// can measure elapsed effect time against its own `World.getTime()` without the unbounded skew
    /// that `Entity.age` carries (client and server age counters start whenever each side first sees
    /// the entity).
    record Effect(StatusEffect effect, int amplifier, long appliedAtWorldTime) { }
    static List<Effect> effectsOf(LivingEntity entity) {
        return ((Provider)entity).SpellEngine_syncedStatusEffects();
    }

    public interface Provider {
        List<Effect> SpellEngine_syncedStatusEffects();
    }
}
