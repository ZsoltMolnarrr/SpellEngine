package net.spell_engine.mixin.entity;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.spell_engine.api.effect.Protection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public class PlayerProtectionEffectMixin {
    @Inject(method = "isInvulnerableTo", at = @At("HEAD"), cancellable = true)
    private void isInvulnerable_SpellEngine(DamageSource damageSource, CallbackInfoReturnable<Boolean> cir) {
        // Check if the player has a protection effect
        if (Protection.tryProtect((PlayerEntity) (Object) this, damageSource)) {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }
}
