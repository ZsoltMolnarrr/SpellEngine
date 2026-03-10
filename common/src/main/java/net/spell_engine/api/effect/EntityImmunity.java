package net.spell_engine.api.effect;

import net.minecraft.entity.Entity;

@Deprecated(forRemoval = true)
public interface EntityImmunity {
    @Deprecated
    enum Type {
        EXPLOSION,
        AREA_EFFECT
    }

    @Deprecated
    boolean isImmuneTo(Type type);
    @Deprecated
    void setImmuneTo(Type type, int ticks);
    @Deprecated
    static void setImmune(Entity entity, Type type, int ticks) {
        ((EntityImmunity)entity).setImmuneTo(type, ticks);
    }
}
