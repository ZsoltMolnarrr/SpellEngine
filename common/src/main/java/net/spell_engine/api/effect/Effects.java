package net.spell_engine.api.effect;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.spell_engine.rpg_series.config.EffectConfig;
import net.spell_power.api.ModifierDefinitions;

import java.util.LinkedHashMap;
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

        /// Reads {@link #entry} back out of the registry, for a loader that registered the effect itself
        /// (Forge's `RegisterEvent` helper returns void where `Registry.registerReference` returns the entry).
        /// Idempotent; throws naming the id if the effect never reached the registry.
        public void link() {
            if (entry != null) { return; }
            entry = Registries.STATUS_EFFECT.getEntry(RegistryKey.of(RegistryKeys.STATUS_EFFECT, id))
                    .orElseThrow(() -> new IllegalStateException(
                            "Status effect " + id + " is not in the registry — register it first"));
        }
    }

    /// Idempotent: attribute modifiers are attached once, registry writes are skipped for ids already present.
    public static void register(List<Entry> entries, Map<String, EffectConfig> effects) {
        effectsToRegister(entries, effects).forEach((id, effect) ->
                Registry.register(Registries.STATUS_EFFECT, id, effect));
        linkEntries(entries);
    }

    /// Applies configuration (config defaults, attribute modifiers) to `entries` and returns every effect
    /// that still needs registering, keyed by the id it registers under. Creation only — nothing is written
    /// here, so a loader that registers status effects itself (Forge) iterates this instead of calling
    /// {@link #register}. Follow it with {@link #linkEntries}: consumers read `Entry#entry` (e.g.
    /// `Protection.register`, `InstantCast.register`), which only the register-reference path fills in.
    ///
    /// Reads the ATTRIBUTE registry while attaching modifiers. That is safe from the status-effect window:
    /// `attribute` is event 4, `mob_effect` event 5.
    public static Map<Identifier, StatusEffect> effectsToRegister(List<Entry> entries, Map<String, EffectConfig> effects) {
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
                    // A blank attribute id is the "no modifier" shape, not a lookup failure — skip it quietly.
                    if (!modifier.hasAttribute()) { continue; }
                    var attributeId = Identifier.tryParse(modifier.attribute);
                    var attribute = attributeId != null ? Registries.ATTRIBUTE.get(attributeId) : null;
                    if (attribute == null) {
                        System.err.println("Failed to resolve EntityAttribute with id: `" + modifier.attribute
                                + "` requested by status effect: " + entry.id);
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

        var toRegister = new LinkedHashMap<Identifier, StatusEffect>();
        for (var entry: entries) {
            if (entry.entry != null || Registries.STATUS_EFFECT.containsId(entry.id)) { continue; }
            toRegister.put(entry.id, entry.effect);
        }
        return toRegister;
    }

    /// Populates every `entry` field from the registry. Call right after registering `entries` through a
    /// loader-specific helper; {@link #register} already does it on the vanilla path.
    public static void linkEntries(List<Entry> entries) {
        for (var entry: entries) {
            entry.link();
        }
    }
}
