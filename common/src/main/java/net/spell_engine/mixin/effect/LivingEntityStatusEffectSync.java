package net.spell_engine.mixin.effect;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.spell_engine.api.effect.EntityTints;
import net.spell_engine.api.effect.Synchronized;
import net.spell_engine.api.spell.fx.ModelEffectAttachment;
import net.spell_engine.internals.SpellEngineAttachments;
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

/// Server-side publisher of the per-living-entity synced state kept in `SpellEngineAttachments`:
/// the `Synchronized` status effects and the blended `EntityTints` tint (re-resolved whenever the
/// effect set changes), and the expiry of attached model FX. Readers go through the attachments
/// directly (`Synchronized.effectsOf`, `EntityTints.currentTint`, `ModelEffectAttachment.of`).
@Mixin(LivingEntity.class)
public abstract class LivingEntityStatusEffectSync extends Entity {
    @Shadow @Final private Map<Holder<MobEffect>, MobEffectInstance> activeEffects;

    public LivingEntityStatusEffectSync(EntityType<?> type, Level world) {
        super(type, world);
    }

    /**
     * `updatePotionVisibility` is called upon effects of the entity are changed.
     */
    @Inject(method = "updateInvisibilityStatus", at = @At("HEAD"))
    private void updatePotionVisibility_HEAD_SpellEngine_SyncEffects(CallbackInfo ci) {
        if (level().isClientSide()) { return; }
        var entity = (LivingEntity) (Object) this;
        SpellEngineAttachments.SYNCED_EFFECTS.set(entity,
                activeEffects.isEmpty() ? List.of() : SpellEngine_synchronizedEffects());
        SpellEngineAttachments.TINT_ARGB.set(entity, EntityTints.resolve(entity));
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void tick_TAIL_SpellEngine_ModelFx(CallbackInfo ci) {
        if (level().isClientSide()) { return; }
        ModelEffectAttachment.expire((LivingEntity) (Object) this, level().getGameTime());
    }

    /// The `Synchronized` subset of the active effects, each keeping the world time it was first
    /// applied at: an effect already published reuses its `appliedAtWorldTime`, a fresh one takes
    /// the current world time. This keeps `appliedAtWorldTime` authoritative on the server, which
    /// is the only side encoding.
    @Unique
    private List<Synchronized.Effect> SpellEngine_synchronizedEffects() {
        var previous = SpellEngineAttachments.SYNCED_EFFECTS.get(this);
        var effects = new ArrayList<Synchronized.Effect>();
        for (var entry : activeEffects.entrySet()) {
            var effect = entry.getKey().value();
            if (!((Synchronized) effect).shouldSynchronize()) { continue; }
            long appliedAtWorldTime = level().getGameTime();
            for (var synced : previous) {
                if (synced.effect() == effect) {
                    appliedAtWorldTime = synced.appliedAtWorldTime();
                    break;
                }
            }
            effects.add(new Synchronized.Effect(effect, entry.getValue().getAmplifier(), appliedAtWorldTime));
        }
        return List.copyOf(effects);
    }
}
