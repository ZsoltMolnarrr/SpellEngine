package net.spell_engine.mixin.evasion;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.spell_engine.api.entity.EvasionLogic;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PersistentProjectileEntity.class)
public class ProjectileEvasionMixin {

    @Nullable private DamageSource currentlyUsedDamageSource = null;

    @WrapOperation(
            method = "onEntityHit",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/Entity;damage(Lnet/minecraft/entity/damage/DamageSource;F)Z"
            )
    )
    private boolean entityHit_SpellEngine_SaveDamageSource(Entity instance, DamageSource source, float amount, Operation<Boolean> original) {
        currentlyUsedDamageSource = source;
        return original.call(instance, source, amount);
    }

    @Inject(
            method = "onEntityHit",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/Entity;setFireTicks(I)V"
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
