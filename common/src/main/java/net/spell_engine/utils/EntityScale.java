package net.spell_engine.utils;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

/// 1.20.1 has no `LivingEntity#getScale()` / `GENERIC_SCALE` attribute (1.20.5+); the only vanilla size
/// multiplier is the baby factor, which is what this reports for living entities (`1` for everything else).
public class EntityScale {
    public static float of(Entity entity) {
        if (entity instanceof LivingEntity living) {
            return living.getScaleFactor();
        }
        return 1F;
    }
}
