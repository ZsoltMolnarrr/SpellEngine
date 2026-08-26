package net.spell_engine.api.effect;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.spell_engine.api.spell.fx.ParticleGroup;
import net.spell_engine.fx.ParticleHelper;
import net.spell_engine.utils.SoundHelper;
import net.spell_engine.utils.StatusEffectUtil;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DISCLAIMER: This API only works on PlayerEntities (due to performance)
 */
public class Protection {
    public record Pop(List<ParticleGroup> particles, @Nullable SoundEvent sound) { }
    public record Entry(Holder<MobEffect> effectEntry, TagKey<DamageType> protects,
                        int decrement, Pop onDecrement, Pop onRemove) { }
    public static final Map<ResourceKey<MobEffect>, Entry> PROTECTIONS = new HashMap<>();

    public static void register(Holder<MobEffect> effectEntry, Pop pop) {
        register(effectEntry.unwrapKey().get(), new Entry(effectEntry, null, 1, pop, pop));
    }

    public static void register(Holder<MobEffect> effectEntry, TagKey<DamageType> protects, Pop pop) {
        register(effectEntry.unwrapKey().get(), new Entry(effectEntry, protects, 1, pop, pop));
    }

    public static void register(ResourceKey<MobEffect> key, Entry entry) {
        PROTECTIONS.put(key, entry);
    }

    public static boolean tryProtect(LivingEntity entity, DamageSource damageSource) {
        for (var entry: entity.getActiveEffectsMap().entrySet()) {
            var optionalKey = entry.getKey().unwrapKey();
            if (optionalKey.isEmpty()) { // Should never happen, added due to some incompatibility crash
                continue;
            }
            var key = optionalKey.get();
            var protection = PROTECTIONS.get(key);
            if (protection != null) {
                if (protection.protects != null && !damageSource.is(protection.protects)) {
                    continue; // This protection does not apply to this damage type
                }
                var effect = entry.getValue();
                var newAmplifier = effect.getAmplifier() - protection.decrement;

                var pop = newAmplifier < 0 ? protection.onRemove : protection.onDecrement;
                if (pop != null) {
                    ParticleHelper.sendBatches(entity, pop.particles);
                    if (pop.sound != null) {
                        SoundHelper.playSoundEvent(entity.level(), entity, pop.sound);
                    }
                }
                StatusEffectUtil.applyChanges(entity, List.of(
                        new StatusEffectUtil.Diff(effect, newAmplifier)
                ));
                return true;
            }
        }
        return false;
    }
}
