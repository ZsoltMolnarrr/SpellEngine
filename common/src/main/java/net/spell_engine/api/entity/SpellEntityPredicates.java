package net.spell_engine.api.entity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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

    public static final Entry TYPE_MATCHES = register(Identifier.fromNamespaceAndPath(SpellEngineMod.ID, "type_matches"), args -> {
        if (args.param == null) {
            return true;
        }
        return PatternMatching.matches(args.entity.getType().builtInRegistryHolder(), Registries.ENTITY_TYPE, args.param);
    });

    public static final Entry HAS_EFFECT = register(Identifier.fromNamespaceAndPath(SpellEngineMod.ID, "has_effect"), args -> {
        if (args.param == null) {
            return true;
        }
        if (args.entity instanceof LivingEntity livingEntity) {
            var id = Identifier.parse(args.param);
            var effect = BuiltInRegistries.MOB_EFFECT.get(id);
            if (effect.isEmpty()) {
                return false;
            }
            return livingEntity.hasEffect(effect.get());
        }
        return false;
    });

    public static final Entry HAS_BAD_EFFECT = register(Identifier.fromNamespaceAndPath(SpellEngineMod.ID, "has_bad_effect"), args -> {
        if (args.entity instanceof LivingEntity livingEntity) {
            for (var instance: livingEntity.getActiveEffects()) {
                var effect = instance.getEffect().value();
                if (!effect.isBeneficial()) {
                    return true;
                }
            }
        }
        return false;
    });

    public static final Entry HAS_GOOD_EFFECT = register(Identifier.fromNamespaceAndPath(SpellEngineMod.ID, "has_good_effect"), args -> {
        if (args.entity instanceof LivingEntity livingEntity) {
            for (var instance : livingEntity.getActiveEffects()) {
                var effect = instance.getEffect().value();
                if (effect.isBeneficial()) {
                    return true;
                }
            }
        }
        return false;
    });

    public static final Entry IS_ON_FIRE = register(Identifier.fromNamespaceAndPath(SpellEngineMod.ID, "is_on_fire"), args -> {
        return args.entity.isOnFire();
    });
    public static final Entry IS_POISONED = hasEffectOptimized(MobEffects.POISON.unwrapKey().get().identifier());

    public static Entry registerOrGet(Identifier id, Predicate<Input> predicate) {
        var entry = get(id);
        if (entry == null) {
            entry = register(id, predicate);
        }
        return entry;
    }

    public static Entry hasEffectOptimized(Identifier effectId) {
        var predicateId = Identifier.fromNamespaceAndPath("has_effect_optimized", effectId.getNamespace() + "." + effectId.getPath());
        return registerOrGet(predicateId, args -> {
            if (args.entity instanceof LivingEntity livingEntity) {
                return livingEntity.hasEffect(BuiltInRegistries.MOB_EFFECT.get(effectId).orElse(null));
            }
            return false;
        });
    }
}
