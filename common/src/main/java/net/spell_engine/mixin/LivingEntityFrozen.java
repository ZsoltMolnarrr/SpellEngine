package net.spell_engine.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.spell_engine.SpellEngineMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityFrozen {
    @Shadow public abstract boolean hasStatusEffect(StatusEffect effect);

    @Inject(method = "baseTick", at = @At("TAIL"))
    public void baseTick_TAIL_FrozenByStatusEffect(CallbackInfo ci) {
        var entity = (LivingEntity) ((Object)this);
        entity.inPowderSnow = entity.inPowderSnow || hasStatusEffect(SpellEngineMod.frozen);
    }

    @Inject(method = "jump", at = @At("HEAD"), cancellable = true)
    public void jump_HEAD_NoJumpingWhileFrozen(CallbackInfo ci) {
        if (hasStatusEffect(SpellEngineMod.frozen)) {
            ci.cancel();
        }
    }
}
