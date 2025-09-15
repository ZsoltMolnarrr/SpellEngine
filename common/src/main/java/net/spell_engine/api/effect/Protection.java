package net.spell_engine.api.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.sound.SoundEvent;
import net.spell_engine.api.spell.fx.ParticleBatch;
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
    public record Pop(ParticleBatch[] particles, @Nullable SoundEvent sound) { }
    public record Entry(RegistryEntry<StatusEffect> effectEntry, TagKey<DamageType> protects,
                        int decrement, Pop onDecrement, Pop onRemove) { }
    public static final Map<RegistryKey<StatusEffect>, Entry> PROTECTIONS = new HashMap<>();

    public static void register(RegistryEntry<StatusEffect> effectEntry, Pop pop) {
        register(effectEntry.getKey().get(), new Entry(effectEntry, null, 1, pop, pop));
    }

    public static void register(RegistryEntry<StatusEffect> effectEntry, TagKey<DamageType> protects, Pop pop) {
        register(effectEntry.getKey().get(), new Entry(effectEntry, protects, 1, pop, pop));
    }

    public static void register(RegistryKey<StatusEffect> key, Entry entry) {
        PROTECTIONS.put(key, entry);
    }

    public static boolean tryProtect(LivingEntity entity, DamageSource damageSource) {
        for (var entry: entity.getActiveStatusEffects().entrySet()) {
            var optionalKey = entry.getKey().getKey();
            if (optionalKey.isEmpty()) { // Should never happen, added due to some incompatibility crash
                continue;
            }
            var key = optionalKey.get();
            var protection = PROTECTIONS.get(key);
            if (protection != null) {
                if (protection.protects != null && !damageSource.isIn(protection.protects)) {
                    continue; // This protection does not apply to this damage type
                }
                var effect = entry.getValue();
                var newAmplifier = effect.getAmplifier() - protection.decrement;

                var pop = newAmplifier < 0 ? protection.onRemove : protection.onDecrement;
                if (pop != null) {
                    ParticleHelper.sendBatches(entity, pop.particles);
                    if (pop.sound != null) {
                        SoundHelper.playSoundEvent(entity.getWorld(), entity, pop.sound);
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
