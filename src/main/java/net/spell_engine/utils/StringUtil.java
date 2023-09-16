package net.spell_engine.utils;

import org.jetbrains.annotations.Nullable;

public class StringUtil {
    public static boolean matching(@Nullable String s1, @Nullable String s2) {
        if (s1 == null && s2 == null) {
            return true;
        }
        if (s1 != null && s2 != null) {
            return s1.equals(s2);
        }
        return false;
    }
}
