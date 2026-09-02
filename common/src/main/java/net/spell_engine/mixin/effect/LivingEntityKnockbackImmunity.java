package net.spell_engine.mixin.effect;

import net.minecraft.world.entity.LivingEntity;
import net.spell_engine.api.effect.KnockbackImmunity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/// Cancels knockback taken from damage while the entity carries a `KnockbackImmunity` effect.
///
/// Scans the active effects directly: `knockback` runs once per damage event (not per tick) and the
/// map is normally empty, so a cached flag bought nothing measurable — and a cache refreshed from
/// `updateDirtyEffects` would be a tracker-time snapshot, going stale for a knockback delivered in
/// the same tick the effect was applied.
@Mixin(LivingEntity.class)
public abstract class LivingEntityKnockbackImmunity {
    // 26.2: `knockback` split into a 5-arg and the terminal 6-arg overload; cancel the terminal one (the other delegates)
    @Inject(method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V", at = @At("HEAD"), cancellable = true)
    private void knockback_HEAD_KnockbackImmunity_SpellEngine(double strength, double x, double z, net.minecraft.world.damagesource.DamageSource source, float damage, boolean comesFromEffect, CallbackInfo ci) {
        var entity = (LivingEntity) (Object) this;
        if (KnockbackImmunity.anyImmune(entity.getActiveEffectsMap().keySet())) {
            ci.cancel();
        }
    }
}
