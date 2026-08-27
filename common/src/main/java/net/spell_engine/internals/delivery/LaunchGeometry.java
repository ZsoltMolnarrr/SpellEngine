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
        return launchPoint(caster, forward, 1F);
    }

    /// The launch point in a single render frame: position and facing are both taken at `partialTick`,
    /// the way vanilla's own frame raycast does it (`Entity#pick` = `getEyePosition(partialTick)` +
    /// `getViewVector(partialTick)`). Server-side callers pass `1F`, which is `position()` / `getLookAngle()`
    /// exactly, so the tick-time geometry is unchanged.
    public static Vec3 launchPoint(LivingEntity caster, float forward, float partialTick) {
        Vec3 look = caster.getViewVector(partialTick).scale(forward * caster.getAgeScale());
        return caster.getPosition(partialTick).add(0, launchHeight(caster), 0).add(look);
    }
}
