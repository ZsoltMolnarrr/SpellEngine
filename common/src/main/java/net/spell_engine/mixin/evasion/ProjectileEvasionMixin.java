package net.spell_engine.mixin.evasion;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.EntityHitResult;
import net.spell_engine.api.entity.EvasionLogic;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractArrow.class)
public class ProjectileEvasionMixin {

    @Nullable private DamageSource currentlyUsedDamageSource = null;

    @WrapOperation(
            method = "onHitEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;hurtOrSimulate(Lnet/minecraft/world/damagesource/DamageSource;F)Z"
            )
    )
    private boolean entityHit_SpellEngine_SaveDamageSource(Entity instance, DamageSource source, float amount, Operation<Boolean> original) {
        currentlyUsedDamageSource = source;
        return original.call(instance, source, amount);
    }

    @Inject(
            method = "onHitEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;setRemainingFireTicks(I)V"
            ),
            cancellable = true
    )
    private void entityHit_SpellEngine_Evasion(EntityHitResult entityHitResult, CallbackInfo ci) {
        if (entityHitResult.getEntity() instanceof EvasionLogic.Evader evader) {
            if (evader.getLastEvaded() == currentlyUsedDamageSource) {
                ci.cancel();
            }
        }
    }
}
