package net.spell_engine.internals.delivery;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

/// Where a spell leaves its caster. Shared by the server-side launch of projectiles and by the
/// client-side renderers that have to draw beams and particles from the same point.
public class LaunchGeometry {

    public static float launchPointOffsetDefault = 0.5F;

    public static float launchHeight(LivingEntity livingEntity) {
        var eyeHeight = livingEntity.getStandingEyeHeight();
        var shoulderDistance = livingEntity.getHeight() * 0.15;
        return (float) ((eyeHeight - shoulderDistance) * livingEntity.getScaleFactor());
    }

    public static Vec3d launchPoint(LivingEntity caster) {
        return launchPoint(caster, launchPointOffsetDefault);
    }

    public static Vec3d launchPoint(LivingEntity caster, float forward) {
        Vec3d look = caster.getRotationVector().multiply(forward * caster.getScaleFactor());
        return caster.getPos().add(0, launchHeight(caster), 0).add(look);
    }
}
