package net.spell_engine.mixin.effect;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.World;
import net.spell_engine.api.effect.Synchronized;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Mixin(LivingEntity.class)
public abstract class LivingEntityStatusEffectSync extends Entity implements Synchronized.Provider {
    @Shadow @Final private Map<RegistryEntry<StatusEffect>, StatusEffectInstance> activeStatusEffects;

    private final ArrayList<Synchronized.Effect> SpellEngine_syncedStatusEffects = new ArrayList();
    private static final TrackedData<String> SPELL_ENGINE_SYNCED_EFFECTS = DataTracker.registerData(LivingEntity.class, TrackedDataHandlerRegistry.STRING);

    public LivingEntityStatusEffectSync(EntityType<?> type, World world) {
        super(type, world);
    }

    @Inject(method = "initDataTracker", at = @At("TAIL"))
    private void initDataTracker_TAIL_SpellEngine_SyncEffects(DataTracker.Builder builder, CallbackInfo ci) {
        builder.add(SPELL_ENGINE_SYNCED_EFFECTS, "");
    }

    /**
     * `updatePotionVisibility` is called upon effects of the entity are changed.
     */
    @Inject(method = "updatePotionVisibility", at = @At("HEAD"))
    private void updatePotionVisibility_HEAD_SpellEngine_SyncEffects(CallbackInfo ci) {
        if (activeStatusEffects.isEmpty()) {
            dataTracker.set(SPELL_ENGINE_SYNCED_EFFECTS, "");
        } else {
            dataTracker.set(SPELL_ENGINE_SYNCED_EFFECTS, SpellEngine_encodedStatusEffects());
        }
    }

//    @Inject(method = "tickStatusEffects", at = @At("TAIL"))
//    private void tickStatusEffects_TAIL_SpellEngine_SyncEffects(CallbackInfo ci) {
//        SpellEngine_syncedStatusEffects.clear();
//        SpellEngine_syncedStatusEffects.addAll(SpellEngine_decodeStatusEffects());
//    }
    @Inject(method = "onTrackedDataSet", at = @At("TAIL"))
    private void onTrackedDataSet_TAIL_SpellEngine_SyncEffects(TrackedData<?> data, CallbackInfo ci) {
        if (SPELL_ENGINE_SYNCED_EFFECTS.equals(data)) {
            SpellEngine_syncedStatusEffects.clear();
            SpellEngine_syncedStatusEffects.addAll(SpellEngine_decodeStatusEffects());
        }
    }

    private String SpellEngine_encodedStatusEffects() {
        StringBuilder builder = new StringBuilder();
        int i = 0;
        for (var entry : activeStatusEffects.entrySet()) {
            var effect = entry.getKey().value();
            if (((Synchronized)effect).shouldSynchronize()) {
                int id = Registries.STATUS_EFFECT.getRawId(effect);
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

    private List<Synchronized.Effect> SpellEngine_decodeStatusEffects() {
        var string = dataTracker.get(SPELL_ENGINE_SYNCED_EFFECTS);
        var effects = new ArrayList<Synchronized.Effect>();
        for (var effect : string.split("-")) {
            var components = effect.split(":");
            if (components.length != 2) {
                continue;
            }
            int rawId = Integer.valueOf(components[0]);
            int amplifier = Integer.valueOf(components[1]);
            var statusEffect = Registries.STATUS_EFFECT.get(rawId);
            if (statusEffect != null) {
                effects.add(new Synchronized.Effect(statusEffect, amplifier));
            }
        }
        return effects;
    }

    public List<Synchronized.Effect> SpellEngine_syncedStatusEffects() {
        return SpellEngine_syncedStatusEffects;
    }
}