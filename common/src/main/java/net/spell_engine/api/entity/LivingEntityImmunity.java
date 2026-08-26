package net.spell_engine.api.entity;

import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.spell_engine.entity.DamageSourceExtension;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class LivingEntityImmunity {
    public record Entry(
            @Nullable DamageType damageType,
            @Nullable TagKey<DamageType> damageTypeTag,
            @Nullable Boolean damageIndirect,
            boolean effectAnyHarmful,
            int validUntil
    ) {
        public boolean protectsAgainst(DamageSource source) {
            var sourceType = source.type();
            if (damageType != null) {
                if (sourceType == null || sourceType != damageType) return false;
            }
            if (damageTypeTag != null) {
                if (sourceType == null || !source.is(damageTypeTag)) return false;
            }
            if (damageIndirect != null) {
                var isInDirect = ((DamageSourceExtension)source).isSpellIndirect();
                if (isInDirect != damageIndirect) return false;
            }
            return true;
        }
    }

    public static boolean isImmune(LivingEntity entity, DamageSource source) {
        var invulnerabilities = ((Owner)entity).getImmunities();
        return isDamageProtected(invulnerabilities, source);
    }

    public static boolean isDamageProtected(List<Entry> invulnerabilities, DamageSource source) {
        for (var entry: invulnerabilities) {
            if (entry.protectsAgainst(source)) {
                return true;
            }
        }
        return false;
    }

    public interface Owner {
        void addImmunity(Entry entry);
        List<Entry> getImmunities();
    }

    public static void apply(LivingEntity livingEntity, DamageType damageType, int ticks) {
        apply(livingEntity, damageType, null, null, ticks);
    }
    public static void apply(LivingEntity livingEntity, DamageType damageType, Boolean indirect, int ticks) {
        apply(livingEntity, damageType, null, indirect, ticks);
    }
    public static void apply(LivingEntity livingEntity, TagKey<DamageType> damageTypeTag, int ticks) {
        apply(livingEntity, null, damageTypeTag, null, ticks);
    }
    public static void apply(LivingEntity livingEntity, TagKey<DamageType> damageTypeTag, Boolean indirect, int ticks) {
        apply(livingEntity, null, damageTypeTag, indirect, ticks);
    }

    public static void apply(LivingEntity livingEntity, @Nullable DamageType damageType, @Nullable TagKey<DamageType> damageTypeTag, @Nullable Boolean indirect, int ticks) {
        apply(livingEntity, damageType, damageTypeTag, indirect, false, ticks);
    }

    public static void apply(LivingEntity livingEntity,
                             @Nullable DamageType damageType, @Nullable TagKey<DamageType> damageTypeTag, @Nullable Boolean indirect,
                             boolean effectAnyHarmful,
                             int ticks) {
        var time = livingEntity.tickCount;
        var validUntil = time + ticks;
        var entry = new Entry(damageType, damageTypeTag, indirect, effectAnyHarmful, validUntil);
        ((Owner)livingEntity).addImmunity(entry);
    }
}
