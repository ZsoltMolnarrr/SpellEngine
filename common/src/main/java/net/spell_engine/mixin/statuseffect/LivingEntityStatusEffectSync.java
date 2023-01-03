package net.spell_engine.mixin.statuseffect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.util.registry.Registry;
import net.spell_engine.api.status_effect.SynchronizedStatusEffect;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

@Mixin(LivingEntity.class)
public abstract class LivingEntityStatusEffectSync implements SynchronizedStatusEffect.Provider {
    @Shadow
    @Final
    private Map<StatusEffect, StatusEffectInstance> activeStatusEffects;

    private final Map<Integer, Integer> SpellEngine_syncedStatusEffects = new HashMap<>();
    private static final TrackedData<String> SPELL_ENGINE_SYNCED_EFFECTS = DataTracker.registerData(LivingEntity.class, TrackedDataHandlerRegistry.STRING);

    @Inject(method = "initDataTracker", at = @At("TAIL"))
    private void initDataTracker_TAIL_SpellEngine_SyncEffects(CallbackInfo ci) {
        var entity = (LivingEntity) ((Object) this);
        entity.getDataTracker().startTracking(SPELL_ENGINE_SYNCED_EFFECTS, "");
    }

    @Inject(method = "updatePotionVisibility", at = @At("HEAD"))
    private void updatePotionVisibility_HEAD_SpellEngine_SyncEffects(CallbackInfo ci) {
        var entity = (LivingEntity) ((Object) this);
        if (activeStatusEffects.isEmpty()) {
            entity.getDataTracker().set(SPELL_ENGINE_SYNCED_EFFECTS, "");
        } else {
            entity.getDataTracker().set(SPELL_ENGINE_SYNCED_EFFECTS, SpellEngine_encodedStatusEffects());
        }
    }

    @Inject(method = "tickStatusEffects", at = @At("TAIL"))
    private void tickStatusEffects_TAIL_SpellEngine_SyncEffects(CallbackInfo ci) {
        SpellEngine_syncedStatusEffects.clear();
        SpellEngine_syncedStatusEffects.putAll(SpellEngine_decodeStatusEffects());
    }

    private String SpellEngine_encodedStatusEffects() {
        StringBuilder builder = new StringBuilder();
        int i = 0;
        for (var entry : activeStatusEffects.entrySet()) {
            var effect = entry.getKey();
            if (effect instanceof SynchronizedStatusEffect) {
                int id = Registry.STATUS_EFFECT.getRawId(entry.getKey());
                int amplifier = entry.getValue().getAmplifier();
                if (i > 0) {
                    builder.append("-");
                }
                builder.append(id).append(":").append(amplifier);
                i += 1;
            }
        }
        return builder.toString();
    }

    private Map<Integer, Integer> SpellEngine_decodeStatusEffects() {
        var entity = (LivingEntity) ((Object) this);
        var string = entity.getDataTracker().get(SPELL_ENGINE_SYNCED_EFFECTS);
        var effects = new HashMap<Integer, Integer>();
        for (var effect : string.split("-")) {
            var components = effect.split(":");
            if (components.length != 2) {
                continue;
            }
            effects.put(Integer.valueOf(components[0]), Integer.valueOf(components[1]));
        }
        return effects;
    }

    public Map<Integer, Integer> SpellEngine_syncedStatusEffects() {
        return SpellEngine_syncedStatusEffects;
    }
}