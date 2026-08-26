package net.spell_engine.mixin.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.spell_engine.api.effect.Protection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public class PlayerProtectionEffectMixin {
    @Inject(method = "isInvulnerableTo", at = @At("HEAD"), cancellable = true)
    private void isInvulnerable_SpellEngine(ServerLevel world, DamageSource damageSource, CallbackInfoReturnable<Boolean> cir) {
        // Check if the player has a protection effect
        if (Protection.tryProtect((Player) (Object) this, damageSource)) {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }
}
