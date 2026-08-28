package net.spell_engine.mixin.effect;

import net.minecraft.world.entity.LivingEntity;
import net.spell_engine.api.effect.EntityTints;
import net.spell_engine.api.effect.Synchronized;
import net.spell_engine.api.spell.fx.ModelEffectAttachment;
import net.spell_engine.internals.SpellEngineAttachments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/// Per-entity visual state published to tracking clients: synced effects, tint, model FX expiry.
///
/// Server-side publisher of the per-living-entity synced state kept in `SpellEngineAttachments`:
/// the `Synchronized` status effects and the blended `EntityTints` tint, both re-resolved whenever
/// the entity's effect set changes. Readers go through the attachments directly
/// (`Synchronized.effectsOf`, `EntityTints.currentTint`).
///
/// Hooked on `updateDirtyEffects` (private, no overrides, sole caller `updateDataBeforeSync`) and
/// guarded by vanilla's own `effectsDirty` flag. Hooking its callee `updateInvisibilityStatus`
/// instead would silently skip two entity kinds that never reach it: `ArmorStand` overrides it
/// without calling super, and `ServerPlayer` skips super while spectating.
@Mixin(LivingEntity.class)
public abstract class LivingEntityStatusEffectSync {
    @Shadow private boolean effectsDirty;

    @Inject(method = "updateDirtyEffects", at = @At("HEAD"))
    private void updateDirtyEffects_HEAD_SpellEngine_SyncEffects(CallbackInfo ci) {
        if (!effectsDirty) { return; }
        var entity = (LivingEntity) (Object) this;
        if (entity.level().isClientSide()) { return; }
        SpellEngineAttachments.SYNCED_EFFECTS.set(entity,
                entity.getActiveEffectsMap().isEmpty() ? List.of() : SpellEngine_synchronizedEffects(entity));
        SpellEngineAttachments.TINT_ARGB.set(entity, EntityTints.resolve(entity));
    }

    /// The `Synchronized` subset of the active effects, each keeping the world time it was first
    /// applied at: an effect already published reuses its `appliedAtWorldTime`, a fresh one takes
    /// the current world time. This keeps `appliedAtWorldTime` authoritative on the server, which
    /// is the only side encoding.
    @Unique
    private static List<Synchronized.Effect> SpellEngine_synchronizedEffects(LivingEntity entity) {
        var previous = SpellEngineAttachments.SYNCED_EFFECTS.get(entity);
        var effects = new ArrayList<Synchronized.Effect>();
        for (var entry : entity.getActiveEffectsMap().entrySet()) {
            var effect = entry.getKey().value();
            if (!((Synchronized) effect).shouldSynchronize()) { continue; }
            long appliedAtWorldTime = entity.level().getGameTime();
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

    /// Server-side expiry of the model FX attached to a living entity
    /// (`SpellEngineAttachments.MODEL_FX`). Nothing vanilla expires them, so they need their own tick;
    /// `ModelEffectAttachment.expire` early-returns on an empty list.
    @Inject(method = "tick", at = @At("TAIL"))
    private void tick_TAIL_SpellEngine_ModelFx(CallbackInfo ci) {
        var entity = (LivingEntity) (Object) this;
        if (entity.level().isClientSide()) { return; }
        ModelEffectAttachment.expire(entity, entity.level().getGameTime());
    }
}
