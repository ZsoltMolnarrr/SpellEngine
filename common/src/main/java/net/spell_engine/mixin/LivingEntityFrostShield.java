package net.spell_engine.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.spell_engine.SpellEngineMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class LivingEntityFrostShield {
    @Inject(method = "isBlocking", at = @At("HEAD"), cancellable = true)
    private void isBlocking_HEAD_FrostShield(CallbackInfoReturnable<Boolean> cir) {
        var entity = (LivingEntity) ((Object)this);
        if (entity.hasStatusEffect(SpellEngineMod.frostShield)) {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }

    @Inject(method = "blockedByShield", at = @At("HEAD"), cancellable = true)
    private void blockedByShield_HEAD_FrostShield(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        var entity = (LivingEntity) ((Object)this);
        if (entity.hasStatusEffect(SpellEngineMod.frostShield) && !source.bypassesArmor()) {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }
}
