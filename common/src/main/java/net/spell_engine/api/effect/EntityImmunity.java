package net.spell_engine.api.effect;

import net.minecraft.entity.Entity;

public interface EntityImmunity {
    enum Type {
        EXPLOSION,
        AREA_EFFECT
    }

    boolean isImmuneTo(Type type);
    void setImmuneTo(Type type, int ticks);

    static void setImmune(Entity entity, Type type, int ticks) {
        ((EntityImmunity)entity).setImmuneTo(type, ticks);
    }
}
