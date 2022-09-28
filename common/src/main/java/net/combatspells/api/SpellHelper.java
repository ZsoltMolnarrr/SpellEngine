package net.combatspells.api;

import net.combatspells.entity.SpellProjectile;
import net.minecraft.entity.LivingEntity;
import net.minecraft.world.World;

public class SpellHelper {
    public static int maximumUseTicks = 72000;

    public static float getCastProgress(int remainingUseTicks, float duration) {
        if (duration <= 0) {
            return 1F;
        }
        var elapsedTicks = maximumUseTicks - remainingUseTicks;
        return Math.min(((float)elapsedTicks) / (duration * 20F), 1F);
    }

    public static void castRelease(World world, LivingEntity caster, Spell spell, int remainingUseTicks) {
        var progress = getCastProgress(remainingUseTicks, spell.cast_duration);
        if (progress >= 1) {
            switch (spell.on_release.action) {
                case SHOOT_PROJECTILE -> {

                    shootProjectile(world, caster, spell.on_release.projectile);
                }
            }
        }
    }

    private static void shootProjectile(World world, LivingEntity caster, Spell.ProjectileData projectileData) {
        // Send target packet

        if (world.isClient) {
            return;
        }


        var x = caster.getX();
        var y  = caster.getEyeY();
        var z  = caster.getZ();
        var projectile = new SpellProjectile(world, caster, x, y, z);
        projectile.setVelocity(caster.getRotationVector().normalize());
        world.spawnEntity(projectile);

        System.out.println("Spawning projectile");
    }
}
