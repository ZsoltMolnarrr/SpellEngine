package net.spell_engine.api.render;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import net.spell_engine.api.effect.CustomParticleStatusEffect;

public class StunParticleSpawner implements CustomParticleStatusEffect.Spawner {

    @Override
    public void spawnParticles(LivingEntity livingEntity, int amplifier) {
        var world = livingEntity.getWorld();
        if (world.isClient && world instanceof ClientWorld clientWorld) {
            var time = livingEntity.age + livingEntity.getWorld().getTime(); // Offset by age so mobs don't look exactly same next to each other
            var angle = Math.toRadians((time % 360) * 18F); // 18 degree per tick = 360 per sec
            var rotated = new Vec3d(0, 0, livingEntity.getWidth() * 0.5F).rotateY((float) angle);
            var spawnPosition = livingEntity
                    .getPos()
                    .add(0, livingEntity.getHeight() * 1.2F, 0)
                    .add(rotated);
            // System.out.println("Spawning stun particle at angle: " + (angle % 360) + " time: " + time);
            clientWorld.addParticle(ParticleTypes.CRIT, true, spawnPosition.x, spawnPosition.y, spawnPosition.z, 0, 0, 0);
        }
    }
}
