package net.spell_engine.api.entity;

import net.spell_engine.utils.RegistryHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.utils.PatternMatching;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

public class SpellEntityPredicates {
    public record Input(Entity entity, Entity other, @Nullable String param) { }
    public record Entry(Identifier id, Predicate<Input> predicate) { }

    private static final Map<String, Entry> entries = new HashMap<>();
    public static Entry register(Identifier id, Predicate<Input> predicate) {
        var entry = new Entry(id, predicate);
        entries.put(id.toString(), entry);
        return entry;
    }
    public static Entry get(Identifier id) {
        return entries.get(id.toString());
    }
    public static Entry get(String idString) {
        return entries.get(idString);
    }

    public static final Entry TYPE_MATCHES = register(new Identifier(SpellEngineMod.ID, "type_matches"), args -> {
        if (args.param == null) {
            return true;
        }
        return PatternMatching.matches(args.entity.getType().getRegistryEntry(), RegistryKeys.ENTITY_TYPE, args.param);
    });

    public static final Entry HAS_EFFECT = register(new Identifier(SpellEngineMod.ID, "has_effect"), args -> {
        if (args.param == null) {
            return true;
        }
        if (args.entity instanceof LivingEntity livingEntity) {
            var id = new Identifier(args.param);
            var effect = RegistryHelper.getEntry(Registries.STATUS_EFFECT, id);
            if (effect.isEmpty()) {
                return false;
            }
            return livingEntity.hasStatusEffect(effect.get().value());
        }
        return false;
    });

    public static final Entry HAS_BAD_EFFECT = register(new Identifier(SpellEngineMod.ID, "has_bad_effect"), args -> {
        if (args.entity instanceof LivingEntity livingEntity) {
            for (var instance: livingEntity.getStatusEffects()) {
                var effect = instance.getEffectType();
                if (!effect.isBeneficial()) {
                    return true;
                }
            }
        }
        return false;
    });

    public static final Entry HAS_GOOD_EFFECT = register(new Identifier(SpellEngineMod.ID, "has_good_effect"), args -> {
        if (args.entity instanceof LivingEntity livingEntity) {
            for (var instance : livingEntity.getStatusEffects()) {
                var effect = instance.getEffectType();
                if (effect.isBeneficial()) {
                    return true;
                }
            }
        }
        return false;
    });

    public static final Entry IS_ON_FIRE = register(new Identifier(SpellEngineMod.ID, "is_on_fire"), args -> {
        return args.entity.isOnFire();
    });
    public static final Entry IS_POISONED = hasEffectOptimized(Registries.STATUS_EFFECT.getId(StatusEffects.POISON));

    public static Entry registerOrGet(Identifier id, Predicate<Input> predicate) {
        var entry = get(id);
        if (entry == null) {
            entry = register(id, predicate);
        }
        return entry;
    }

    public static Entry hasEffectOptimized(Identifier effectId) {
        var predicateId = new Identifier("has_effect_optimized", effectId.getNamespace() + "." + effectId.getPath());
        return registerOrGet(predicateId, args -> {
            if (args.entity instanceof LivingEntity livingEntity) {
                return livingEntity.hasStatusEffect(RegistryHelper.getEntry(Registries.STATUS_EFFECT, effectId).map(RegistryEntry::value).orElse(null));
            }
            return false;
        });
    }
}
