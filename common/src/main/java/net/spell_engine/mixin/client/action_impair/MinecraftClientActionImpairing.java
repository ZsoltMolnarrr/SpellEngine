package net.spell_engine.mixin.client.action_impair;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.spell_engine.api.effect.EntityActionsAllowed;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MinecraftClient.class, priority = 10) // Low value = high priority (applied early)
public class MinecraftClientActionImpairing {
    @Shadow
    @Nullable
    public ClientPlayerEntity player;

    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void doAttack_HEAD_SpellEngine_ActionImpair(CallbackInfoReturnable<Boolean> cir) {
        var actionsAllowed = ((EntityActionsAllowed.ControlledEntity) player).actionImpairing();
        if (!actionsAllowed.players().canAttack()) {
            cir.cancel();
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void handleBlockBreaking_HEAD_SpellEngine_ActionImpair(boolean bl, CallbackInfo ci) {
        var actionsAllowed = ((EntityActionsAllowed.ControlledEntity) player).actionImpairing();
        if (!actionsAllowed.players().canAttack()) {
            ci.cancel();
        }
    }
}