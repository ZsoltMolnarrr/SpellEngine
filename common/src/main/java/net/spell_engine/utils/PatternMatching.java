package net.spell_engine.utils;

import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

public class PatternMatching {
    public static final String ANY = "*";
    public static final String TAG_PREFIX = "#";
    public static final String REGEX_PREFIX = "~";
    public static final String NEGATE_PREFIX = "!";

    public static String regex(String pattern) {
        return REGEX_PREFIX + pattern;
    }

    public static <T> boolean matches(Holder<T> entry, ResourceKey<Registry<T>> registryKey, @Nullable String pattern) {
        if (pattern == null || pattern.isEmpty() || pattern.equals(ANY)) {
            return true;
        }
        if (pattern.startsWith(NEGATE_PREFIX)) {
            return !entryMatches(entry, registryKey, pattern.substring(1));
        } else {
            return entryMatches(entry, registryKey, pattern);
        }
    }

    public static <T> boolean entryMatches(Holder<T> entry, ResourceKey<Registry<T>> registryKey, String pattern) {
        if (pattern.startsWith(TAG_PREFIX)) {
            var tag = TagKey.create(registryKey, Identifier.parse(pattern.substring(1)));
            return entry.is(tag);
        }
        var id = entry.unwrapKey().get().identifier().toString();
        if (pattern.startsWith(REGEX_PREFIX)) {
            return regexMatches(id, pattern.substring(1));
        } else {
            return id.equals(pattern);
        }
    }

    public static boolean regexMatches(String subject, String regex) {
        if (subject == null) {
            return false;
        }
        if (regex == null || regex.isEmpty()) {
            return true;
        }
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(subject);
        return matcher.find();
    }
}
