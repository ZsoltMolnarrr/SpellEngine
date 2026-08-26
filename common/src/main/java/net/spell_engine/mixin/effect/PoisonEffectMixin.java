package net.spell_engine.mixin.effect;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.PoisonMobEffect;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PoisonMobEffect.class)
public class PoisonEffectMixin {
    @WrapOperation(
            method = "applyEffectTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z")
    )
    public boolean applyUpdateEffect_SpellEngine(
            LivingEntity instance, ServerLevel world, DamageSource source, float amount, Operation<Boolean> original,
            ServerLevel contextWorld, LivingEntity entity, int amplifier) {
        var amplifiedAmount = amount * (amplifier + 1);
        var cappedAmount = Math.min(amplifiedAmount, entity.getHealth() - 1.0F);
        return original.call(instance, world, source, cappedAmount);
    }

    @Inject(method = "shouldApplyEffectTickThisTick", at = @At("HEAD"), cancellable = true, require = 0)
    private void canApplyUpdateEffect_SpellEngine(int duration, int amplifier, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(duration % 25 == 0);
        cir.cancel();
    }
}
