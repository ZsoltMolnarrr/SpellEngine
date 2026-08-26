package net.spell_engine.api.effect;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.spell_engine.rpg_series.config.ConfigUtil;
import net.spell_engine.rpg_series.config.EffectConfig;

import java.util.List;
import java.util.Map;

public class Effects {
    public static final class Entry {
        public final Identifier id;
        public final String title;
        public final String description;
        public final MobEffect effect;
        public final EffectConfig defaults;
        public EffectConfig config;
        public Holder<MobEffect> entry;

        public Entry(Identifier id, String title, String description, MobEffect effect) {
            this(id, title, description, effect, EffectConfig.EMPTY);
        }
        public Entry(Identifier id, String title, String description, MobEffect effect, EffectConfig config) {
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

            var modifiers = ConfigUtil.modifiersFrom(entry.id, entry.config.selectedAttributes());
            for (var modifier : modifiers) {
                entry.effect
                        .addAttributeModifier(modifier.attribute(),
                                modifier.modifier().id(),
                                modifier.modifier().amount(),
                                modifier.modifier().operation());
            }
        }

        for (var entry: entries) {
            entry.entry = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, entry.id, entry.effect);
        }
    }
}