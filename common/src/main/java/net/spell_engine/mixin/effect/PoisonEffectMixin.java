package net.spell_engine.mixin.effect;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.PoisonStatusEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PoisonStatusEffect.class)
public class PoisonEffectMixin {
    @WrapOperation(
            method = "applyUpdateEffect",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;damage(Lnet/minecraft/entity/damage/DamageSource;F)Z")
    )
    public boolean applyUpdateEffect_SpellEngine(
            LivingEntity instance, DamageSource source, float amount, Operation<Boolean> original,
            LivingEntity entity, int amplifier) {
        var amplifiedAmount = amount * (amplifier + 1);
        var cappedAmount = Math.min(amplifiedAmount, entity.getHealth() - 1.0F);
        return original.call(instance, source, cappedAmount);
    }

    @Inject(method = "canApplyUpdateEffect", at = @At("HEAD"), cancellable = true, require = 0)
    private void canApplyUpdateEffect_SpellEngine(int duration, int amplifier, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(duration % 25 == 0);
        cir.cancel();
    }
}
