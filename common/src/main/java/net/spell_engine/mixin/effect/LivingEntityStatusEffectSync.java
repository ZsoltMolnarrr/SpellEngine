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
import net.spell_engine.api.effect.EntityTints;
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

// priority < 1000: applied early so SE's TrackedData fields get stable ids (cf. PlayerEntityMixin 555).
@Mixin(value = LivingEntity.class, priority = 556)
public abstract class LivingEntityStatusEffectSync extends Entity implements Synchronized.Provider, ModelEffectAttachment.Provider, EntityTints.Provider {
    @Shadow @Final private Map<RegistryEntry<StatusEffect>, StatusEffectInstance> activeStatusEffects;

    // MARK: Status effect sync

    private final ArrayList<Synchronized.Effect> SpellEngine_syncedStatusEffects = new ArrayList();
    private static final TrackedData<String> SPELL_ENGINE_SYNCED_EFFECTS = DataTracker.registerData(LivingEntity.class, TrackedDataHandlerRegistry.STRING);

    // MARK: Entity tint (see EntityTints)

    private static final TrackedData<Integer> SPELL_ENGINE_TINT_ARGB = DataTracker.registerData(LivingEntity.class, TrackedDataHandlerRegistry.INTEGER);

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
        builder.add(SPELL_ENGINE_TINT_ARGB, EntityTints.NEUTRAL);
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
        dataTracker.set(SPELL_ENGINE_TINT_ARGB, EntityTints.resolve((LivingEntity)(Object)this));
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
                if (oldEffect.isPresent() && oldEffect.get().appliedAtWorldTime() < newEffect.appliedAtWorldTime()) {
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
                // Guard against an id-desynced slot feeding non-JSON here: degrade instead of
                // throwing on the network thread and disconnecting the player.
                try {
                    List<ModelEffectAttachment.Entry> parsed = SpellEngine_gson.fromJson(json, SpellEngine_entryListType);
                    if (parsed != null) SpellEngine_attachedModelFx.addAll(parsed);
                } catch (RuntimeException ignored) { }
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
                long appliedAtWorldTime = SpellEngine_appliedAtWorldTimeFor(effect);
                if (i > 0) {
                    builder.append("-");
                }
                builder.append(id).append(":").append(amplifier).append(":").append(appliedAtWorldTime);
                i += 1;
            }
        }
        return builder.toString();
    }

    /**
     * Resolves the world time at which the given effect was first applied, so it survives re-encoding.
     * For an effect already known (present in the synced list), its original applied time is reused;
     * a freshly applied effect uses the current world time. This keeps `appliedAtWorldTime`
     * authoritative on the encoding side (which only runs server-side, via `updatePotionVisibility`)
     * instead of being re-derived after decode.
     */
    @Unique
    private long SpellEngine_appliedAtWorldTimeFor(StatusEffect effect) {
        for (var synced : SpellEngine_syncedStatusEffects) {
            if (synced.effect() == effect) {
                return synced.appliedAtWorldTime();
            }
        }
        return getWorld().getTime();
    }

    @Unique
    private List<Synchronized.Effect> SpellEngine_decodeStatusEffects() {
        var string = dataTracker.get(SPELL_ENGINE_SYNCED_EFFECTS);
        var effects = new ArrayList<Synchronized.Effect>();
        // Guard against a foreign payload in an id-desynced slot: bail on the whole thing.
        try {
            for (var effect : string.split("-")) {
                var components = effect.split(":");
                if (components.length != 3) {
                    continue;
                }
                int rawId = Integer.valueOf(components[0]);
                int amplifier = Integer.valueOf(components[1]);
                long appliedAtWorldTime = Long.valueOf(components[2]);
                var statusEffect = Registries.STATUS_EFFECT.get(rawId);
                if (statusEffect != null) {
                    effects.add(new Synchronized.Effect(statusEffect, amplifier, appliedAtWorldTime));
                }
            }
        } catch (RuntimeException ignored) { }
        return effects;
    }

    // MARK: Provider implementations

    public List<Synchronized.Effect> SpellEngine_syncedStatusEffects() {
        return SpellEngine_syncedStatusEffects;
    }

    @Override
    public int SpellEngine_entityTintArgb() {
        return dataTracker.get(SPELL_ENGINE_TINT_ARGB);
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
