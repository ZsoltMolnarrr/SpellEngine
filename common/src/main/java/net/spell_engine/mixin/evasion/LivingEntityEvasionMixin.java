package net.spell_engine.mixin.evasion;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.spell_engine.api.entity.EvasionLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class LivingEntityEvasionMixin implements EvasionLogic.Evader {
    @Inject(
            method = "hurtServer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;isSleeping()Z"
            ),
            cancellable = true
    )
    private void damage_SpellEngine_Evasion(ServerLevel world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        var entity = (LivingEntity) (Object) this;
        if (EvasionLogic.tryEvade(entity, amount, source)) {
            EvasionLogic.onEvade(entity, amount, source);
            lastEvaded = source;
            cir.setReturnValue(false);
            cir.cancel();
        }
    }

    private DamageSource lastEvaded = null;
    @Override
    public DamageSource getLastEvaded() {
        return lastEvaded;
    }
    @Override
    public void setLastEvaded(DamageSource source) {
        this.lastEvaded = source;
    }
}
