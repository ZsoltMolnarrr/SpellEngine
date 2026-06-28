package net.spell_engine.mixin.effect;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
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
import net.spell_engine.api.spell.fx.ModelEffectAttachment;
import net.spell_engine.api.spell.fx.ModelEffect;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Mixin(LivingEntity.class)
public abstract class LivingEntityStatusEffectSync extends Entity implements Synchronized.Provider, ModelEffectAttachment.Provider {
    @Shadow @Final private Map<RegistryEntry<StatusEffect>, StatusEffectInstance> activeStatusEffects;

    // MARK: Status effect sync

    private final ArrayList<Synchronized.Effect> SpellEngine_syncedStatusEffects = new ArrayList();
    private static final TrackedData<String> SPELL_ENGINE_SYNCED_EFFECTS = DataTracker.registerData(LivingEntity.class, TrackedDataHandlerRegistry.STRING);

    // MARK: Attached model FX

    private static final Gson SpellEngine_gson = new Gson();
    private static final Type SpellEngine_entryListType = new TypeToken<List<ModelEffectAttachment.Entry>>(){}.getType();
    private static final TrackedData<String> SPELL_ENGINE_MODEL_FX = DataTracker.registerData(LivingEntity.class, TrackedDataHandlerRegistry.STRING);

    @Unique
    private final List<ModelEffectAttachment.Entry> SpellEngine_attachedModelFx = new ArrayList<>();

    // MARK: Constructors

    public LivingEntityStatusEffectSync(EntityType<?> type, World world) {
        super(type, world);
    }

    // MARK: DataTracker init

    @Inject(method = "initDataTracker", at = @At("TAIL"))
    private void initDataTracker_TAIL_SpellEngine_SyncEffects(DataTracker.Builder builder, CallbackInfo ci) {
        builder.add(SPELL_ENGINE_SYNCED_EFFECTS, "");
        builder.add(SPELL_ENGINE_MODEL_FX, "");
    }

    // MARK: Status effect sync — write

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

    // MARK: Model FX — server tick (expiry)

    @Inject(method = "tick", at = @At("TAIL"))
    private void tick_TAIL_SpellEngine_ModelFx(CallbackInfo ci) {
        if (getWorld().isClient || SpellEngine_attachedModelFx.isEmpty()) return;
        boolean changed = SpellEngine_attachedModelFx.removeIf(e -> getWorld().getTime() >= e.expiresAtWorldTime());
        if (changed) {
            dataTracker.set(SPELL_ENGINE_MODEL_FX,
                    SpellEngine_attachedModelFx.isEmpty() ? "" : SpellEngine_gson.toJson(SpellEngine_attachedModelFx));
        }
    }

    // MARK: DataTracker receive (both status effects and model FX)

    @Inject(method = "onTrackedDataSet", at = @At("TAIL"))
    private void onTrackedDataSet_TAIL_SpellEngine_SyncEffects(TrackedData<?> data, CallbackInfo ci) {
        if (SPELL_ENGINE_SYNCED_EFFECTS.equals(data)) {
            var newEffects = SpellEngine_decodeStatusEffects();
            var merged = new ArrayList<Synchronized.Effect>();
            for (var newEffect : newEffects) {
                var oldEffect = SpellEngine_syncedStatusEffects.stream()
                        .filter(e -> e.effect() == newEffect.effect())
                        .findFirst();
                if (oldEffect.isPresent() && oldEffect.get().appliedAtAge() < newEffect.appliedAtAge()) {
                    merged.add(oldEffect.get());
                } else {
                    merged.add(newEffect);
                }
            }
            SpellEngine_syncedStatusEffects.clear();
            SpellEngine_syncedStatusEffects.addAll(merged);
        }
        if (SPELL_ENGINE_MODEL_FX.equals(data)) {
            var json = dataTracker.get(SPELL_ENGINE_MODEL_FX);
            SpellEngine_attachedModelFx.clear();
            if (json != null && !json.isEmpty()) {
                List<ModelEffectAttachment.Entry> parsed = SpellEngine_gson.fromJson(json, SpellEngine_entryListType);
                if (parsed != null) SpellEngine_attachedModelFx.addAll(parsed);
            }
        }
    }

    // MARK: Status effect sync — helpers

    @Unique
    private String SpellEngine_encodedStatusEffects() {
        StringBuilder builder = new StringBuilder();
        int i = 0;
        for (var entry : activeStatusEffects.entrySet()) {
            var effect = entry.getKey().value();
            if (((Synchronized)effect).shouldSynchronize()) {
                int id = Registries.STATUS_EFFECT.getRawId(effect);
                int amplifier = entry.getValue().getAmplifier();
                int appliedAtAge = SpellEngine_appliedAtAgeFor(effect);
                if (i > 0) {
                    builder.append("-");
                }
                builder.append(id).append(":").append(amplifier).append(":").append(appliedAtAge);
                i += 1;
            }
        }
        return builder.toString();
    }

    /**
     * Resolves the age at which the given effect was first applied, so it survives re-encoding.
     * For an effect already known (present in the synced list), its original applied age is reused;
     * a freshly applied effect uses the entity's current age. This keeps `appliedAtAge` authoritative
     * on the encoding side instead of being re-derived after decode.
     */
    @Unique
    private int SpellEngine_appliedAtAgeFor(StatusEffect effect) {
        for (var synced : SpellEngine_syncedStatusEffects) {
            if (synced.effect() == effect) {
                return synced.appliedAtAge();
            }
        }
        return this.age;
    }

    @Unique
    private List<Synchronized.Effect> SpellEngine_decodeStatusEffects() {
        var string = dataTracker.get(SPELL_ENGINE_SYNCED_EFFECTS);
        var effects = new ArrayList<Synchronized.Effect>();
        for (var effect : string.split("-")) {
            var components = effect.split(":");
            if (components.length != 3) {
                continue;
            }
            int rawId = Integer.valueOf(components[0]);
            int amplifier = Integer.valueOf(components[1]);
            int appliedAtAge = Integer.valueOf(components[2]);
            var statusEffect = Registries.STATUS_EFFECT.get(rawId);
            if (statusEffect != null) {
                effects.add(new Synchronized.Effect(statusEffect, amplifier, appliedAtAge));
            }
        }
        return effects;
    }

    // MARK: Provider implementations

    public List<Synchronized.Effect> SpellEngine_syncedStatusEffects() {
        return SpellEngine_syncedStatusEffects;
    }

    @Override
    public List<ModelEffectAttachment.Entry> SpellEngine_getAttachedModelFx() {
        return SpellEngine_attachedModelFx;
    }

    @Override
    public void SpellEngine_attachModelFx(ModelEffect effect, long worldTime) {
        SpellEngine_attachedModelFx.add(new ModelEffectAttachment.Entry(effect, worldTime, effect.duration));
        dataTracker.set(SPELL_ENGINE_MODEL_FX, SpellEngine_gson.toJson(SpellEngine_attachedModelFx));
    }
}
