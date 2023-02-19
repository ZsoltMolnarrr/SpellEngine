package net.spell_engine.mixin.action_impair;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.spell_engine.api.effect.EntityActionsAllowed;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public class PlayerEntityActionImpairing {
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void attack_HEAD_SpellEngine(Entity target, CallbackInfo ci) {
        var actionsAllowed = ((EntityActionsAllowed.ControlledEntity) this).actionImpairing();
        if (!actionsAllowed.players().canAttack()) {
            ci.cancel();
        }
    }
}
