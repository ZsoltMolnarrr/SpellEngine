package net.spell_engine.api.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.spell_engine.api.spell.Spell;

import java.util.HashMap;
import java.util.Map;

public class InstantCast {
    public enum Selection { NONE, SINGLE, TAG }
    public record Args(Selection selection, RegistryKey<Spell> spell, TagKey<Spell> tag) {
        public static Args spell(RegistryKey<Spell> spell) {
            return new Args(Selection.SINGLE, spell, null);
        }
        public static Args tag(TagKey<Spell> tag) {
            return new Args(Selection.TAG, null, tag);
        }
    }
    /// 1.20.1: status effects are plain registry objects, so they key the map directly.
    private static Map<StatusEffect, Args> instantCastEffects = new HashMap<>();

    public static void register(StatusEffect effect, RegistryKey<Spell> spell) {
        register(effect, Args.spell(spell));
    }

    public static void register(StatusEffect effect, TagKey<Spell> tag) {
        register(effect, Args.tag(tag));
    }

    public static void register(RegistryEntry<StatusEffect> effect, RegistryKey<Spell> spell) {
        register(effect.value(), Args.spell(spell));
    }

    public static void register(RegistryEntry<StatusEffect> effect, TagKey<Spell> tag) {
        register(effect.value(), Args.tag(tag));
    }

    public static void register(StatusEffect effect, Args args) {
        instantCastEffects.put(effect, args);
    }

    public static boolean instantify(RegistryEntry<Spell> spellEntry, LivingEntity caster) {
        for (var entry : caster.getActiveStatusEffects().entrySet()) {
            var args = instantCastEffects.get(entry.getValue().getEffectType());
            if (args != null) {
                switch (args.selection) {
                    case NONE -> {
                        return true;
                    }
                    case SINGLE -> {
                        if (args.spell.equals(spellEntry.getKey().get())) {
                            return true;
                        }
                    }
                    case TAG -> {
                        if (spellEntry.isIn(args.tag)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
