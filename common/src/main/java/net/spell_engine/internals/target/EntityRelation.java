package net.spell_engine.internals.target;

public enum EntityRelation {
    ALLY, FRIENDLY, NEUTRAL, HOSTILE, MIXED;

    public static EntityRelation coalesce(EntityRelation value, EntityRelation fallback) {
        if (value != null) {
            return value;
        }
        return fallback;
    }
}
