package net.spell_engine.api.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.spell_engine.internals.SpellEngineAttachments;

import java.util.List;

public interface Synchronized {
    boolean shouldSynchronize();
    MobEffect setSynchronized(boolean value);

    static void configure(MobEffect effect, boolean isSynchronized) {
        ((Synchronized)effect).setSynchronized(isSynchronized);
    }

    /// `appliedAtWorldTime` is the server's `World.getTime()` at the moment the effect was applied.
    /// World time shares an origin and rate on both sides and is re-synced each second, so a client
    /// can measure elapsed effect time against its own `World.getTime()` without the unbounded skew
    /// that `Entity.age` carries (client and server age counters start whenever each side first sees
    /// the entity).
    record Effect(MobEffect effect, int amplifier, long appliedAtWorldTime) { }

    /// The entity's synchronized effects as last published by the server (an immutable snapshot,
    /// stored as a synced entity attachment — see `SpellEngineAttachments.SYNCED_EFFECTS`).
    static List<Effect> effectsOf(LivingEntity entity) {
        return SpellEngineAttachments.SYNCED_EFFECTS.get(entity);
    }
}
