package net.spell_engine.api.effect;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;

public class CustomParticleStatusEffect {
    private static final Map<MobEffect, Spawner> spawners = new HashMap<>();

    public static void register(MobEffect statusEffect, Spawner spawner) {
        spawners.put(statusEffect, spawner);
    }

    public static Spawner spawnerOf(MobEffect statusEffect) {
        return spawners.get(statusEffect);
    }

    public interface Spawner {
        void spawnParticles(LivingEntity livingEntity, int amplifier);
    }
}
