package net.spell_engine.api.status_effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;

import java.util.HashMap;
import java.util.Map;

public class CustomParticleStatusEffect {
    private static final Map<StatusEffect, Spawner> spawners = new HashMap<>();

    public static void register(StatusEffect statusEffect, Spawner spawner) {
        spawners.put(statusEffect, spawner);
    }

    public static Spawner spawnerOf(StatusEffect statusEffect) {
        return spawners.get(statusEffect);
    }

    public interface Spawner {
        void spawnParticles(LivingEntity livingEntity, int amplifier);
    }
}
