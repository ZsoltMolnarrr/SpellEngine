package net.spell_engine.api.effect;

public interface EntityImmunity {
    enum Type {
        EXPLOSION,
        AREA_EFFECT
    }

    boolean isImmuneTo(Type type);
    void setImmuneTo(Type type, int ticks);
}
