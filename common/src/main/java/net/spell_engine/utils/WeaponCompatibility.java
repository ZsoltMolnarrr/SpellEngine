package net.spell_engine.utils;

import net.minecraft.item.SwordItem;
import net.minecraft.util.registry.Registry;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.spell.SpellContainer;
import net.spell_engine.internals.SpellRegistry;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WeaponCompatibility {
    public static void initialize() {
        var config = SpellEngineMod.config;
        var proxyContainer = new SpellContainer(true, null, 0, List.of());
        for(var itemId: Registry.ITEM.getIds()) {
            var item = Registry.ITEM.get(itemId);
            boolean addProxy = false;
            if (config.add_spell_casting_to_swords && item instanceof SwordItem) {
                addProxy = true;
            } else if (config.add_spell_casting_regex != null
                    && !config.add_spell_casting_regex.isEmpty()
                    && matches(itemId.toString(), config.add_spell_casting_regex)) {
                addProxy = true;
            }
            if (addProxy) {
                SpellRegistry.containers.putIfAbsent(itemId, proxyContainer);
            }
        }
    }

    public static boolean matches(String subject, String nullableRegex) {
        if (subject == null) {
            return false;
        }
        if (nullableRegex == null || nullableRegex.isEmpty()) {
            return false;
        }
        Pattern pattern = Pattern.compile(nullableRegex, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(subject);
        return matcher.find();
    }
}
