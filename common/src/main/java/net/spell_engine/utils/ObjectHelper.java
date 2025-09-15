package net.spell_engine.utils;

public class ObjectHelper {
    public static <T> T coalesce(T ...items) {
        for (T i : items) if (i != null) return i;
        return null;
    }
}
