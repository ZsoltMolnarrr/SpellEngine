package net.spell_engine.api.effect;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.spell_engine.rpg_series.config.EffectConfig;
import net.spell_power.api.ModifierDefinitions;

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
        /// `null` until {@link Effects#register} ran (Forge: until the status effect `RegisterEvent`).
        /// 1.20.1 APIs take the raw {@link #effect}; prefer that.
        public RegistryEntry<StatusEffect> entry;
        private boolean modifiersApplied = false;

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

    /// Idempotent: attribute modifiers are attached once, registry writes are skipped for ids already present.
    public static void register(List<Entry> entries, Map<String, EffectConfig> effects) {
        for (var entry: entries) {
            var key = entry.id.toString();
            var current = effects.get(key);
            if (current != null) {
                entry.config = current;
            } else {
                effects.put(key, entry.config);
            }

            if (!entry.modifiersApplied) {
                entry.modifiersApplied = true;
                for (var modifier : entry.config.selectedAttributes()) {
                    var attributeId = new Identifier(modifier.attribute);
                    var attribute = Registries.ATTRIBUTE.get(attributeId);
                    if (attribute == null) {
                        System.err.println("Failed to resolve EntityAttribute with id: " + modifier.attribute);
                        continue;
                    }
                    var modifierId = (modifier.id != null && !modifier.id.isEmpty())
                            ? new Identifier(modifier.id)
                            : entry.id;
                    entry.effect.addAttributeModifier(
                            attribute,
                            ModifierDefinitions.uuid(modifierId).toString(),
                            modifier.value,
                            modifier.operation);
                }
            }
        }

        for (var entry: entries) {
            if (entry.entry != null) { continue; }
            if (Registries.STATUS_EFFECT.containsId(entry.id)) {
                entry.entry = Registries.STATUS_EFFECT.getEntry(Registries.STATUS_EFFECT.get(entry.id));
                continue;
            }
            entry.entry = Registry.registerReference(Registries.STATUS_EFFECT, entry.id, entry.effect);
        }
    }
}
