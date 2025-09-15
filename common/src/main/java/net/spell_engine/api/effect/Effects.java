package net.spell_engine.api.effect;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.spell_engine.api.config.ConfigUtil;
import net.spell_engine.api.config.EffectConfig;

import java.util.List;
import java.util.Map;

public class Effects {
    public static final class Entry {
        public final Identifier id;
        public final String title;
        public final String description;
        public final StatusEffect effect;
        public final EffectConfig defaults;
        public EffectConfig config;
        public RegistryEntry<StatusEffect> entry;

        public Entry(Identifier id, String title, String description, StatusEffect effect) {
            this(id, title, description, effect, EffectConfig.EMPTY);
        }
        public Entry(Identifier id, String title, String description, StatusEffect effect, EffectConfig config) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.effect = effect;
            this.defaults = config;
            this.config = config;
        }

        public EffectConfig config() {
            return config;
        }
    }

    public static void register(List<Entry> entries, Map<String, EffectConfig> effects) {
        for (var entry: entries) {
            var key = entry.id.toString();
            var current = effects.get(key);
            if (current != null) {
                entry.config = current;
            } else {
                effects.put(key, entry.config);
            }

            var modifiers = ConfigUtil.modifiersFrom(entry.id, entry.config.attributes());
            for (var modifier : modifiers) {
                entry.effect
                        .addAttributeModifier(modifier.attribute(),
                                modifier.modifier().id(),
                                modifier.modifier().value(),
                                modifier.modifier().operation());
            }
        }

        for (var entry: entries) {
            entry.entry = Registry.registerReference(Registries.STATUS_EFFECT, entry.id, entry.effect);
        }
    }
}