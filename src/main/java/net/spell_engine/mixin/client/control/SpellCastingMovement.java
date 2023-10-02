package net.spell_engine.mixin.client.control;

import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.internals.casting.SpellCasterClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public class SpellCastingMovement {
    @Shadow public Input input;
    @Shadow protected int ticksLeftToDoubleTapSprint;

    @Inject(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/input/Input;tick(ZF)V", shift = At.Shift.AFTER))
    private void tickMovement_ModifyInput(CallbackInfo ci) {
        var player = (ClientPlayerEntity) (Object) this;
        var caster = (SpellCasterClient) player;
        if (caster.isCastingSpell() && !player.hasVehicle()) {
            var multiplier = SpellEngineMod.config.movement_speed_while_casting_spell;
            input.movementSideways *= multiplier;
            input.movementForward *= multiplier;
            ticksLeftToDoubleTapSprint = 0;
        }
    }
}
