package net.spell_engine.mixin.action_impair;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.spell_engine.api.effect.EntityActionsAllowed;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public class PlayerEntityActionImpairing {
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void attack_HEAD_SpellEngine(Entity target, CallbackInfo ci) {
        if (EntityActionsAllowed.isImpaired((Player) ((Object) this),
                EntityActionsAllowed.Player.ATTACK)) {
            ci.cancel();
        }
    }
}
