package net.combatspells.api;

import net.minecraft.entity.LivingEntity;

public class SpellHelper {
    public static int maximumUseTicks = 72000;

    public static float getCastProgress(int remainingUseTicks, float duration) {
        if (duration <= 0) {
            return 1F;
        }
        var elapsedTicks = maximumUseTicks - remainingUseTicks;
        return Math.min(((float)elapsedTicks) / (duration * 20F), 1F);
    }

    public static void castRelease(LivingEntity caster, Spell spell, int remainingUseTicks) {
        var progress = getCastProgress(remainingUseTicks, spell.cast_duration);
        if (progress >= 1) {
            switch (spell.on_release.action) {
                case SHOOT_PROJECTILE -> {

                }
            }
        }
    }

    private static void shootProjectile(LivingEntity caster, Spell.ProjectileData projectileData) {
        if (caster.world.isClient) {
            return;
        }
        // Send target packet
    }
}
