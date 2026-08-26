package net.spell_engine.internals.delivery;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/// Where a spell leaves its caster. Shared by the server-side launch of projectiles and by the
/// client-side renderers that have to draw beams and particles from the same point.
public class LaunchGeometry {

    public static float launchPointOffsetDefault = 0.5F;

    public static float launchHeight(LivingEntity livingEntity) {
        var eyeHeight = livingEntity.getEyeHeight();
        var shoulderDistance = livingEntity.getBbHeight() * 0.15;
        return (float) ((eyeHeight - shoulderDistance) * livingEntity.getAgeScale());
    }

    public static Vec3 launchPoint(LivingEntity caster) {
        return launchPoint(caster, launchPointOffsetDefault);
    }

    public static Vec3 launchPoint(LivingEntity caster, float forward) {
        Vec3 look = caster.getLookAngle().scale(forward * caster.getAgeScale());
        return caster.position().add(0, launchHeight(caster), 0).add(look);
    }
}
