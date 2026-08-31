package net.spell_engine.mixin.client.control;

import org.spongepowered.asm.mixin.Unique;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec2;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.internals.casting.SpellCaster;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public class SpellCastingMovement {
    @Shadow public ClientInput input;
    @Shadow protected int sprintTriggerTime;

    @Inject(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/ClientInput;tick()V", shift = At.Shift.AFTER))
    private void tickMovement_ModifyInput(CallbackInfo ci) {
        var player = (LocalPlayer) (Object) this;
        var caster = (SpellCaster.Client) player;
        var process = caster.getSpellCastProcess();
        if (process != null && process.spell().value().active.cast != null && !player.isPassenger()) {
            var multiplier = process.spell().value().active.cast.movement_speed * SpellEngineMod.config.movement_multiplier_speed_while_casting;
            scaleMovement(input, multiplier);
            sprintTriggerTime = 0;
        }
        var attack = caster.getCurrentSkillAttack();
        if (attack != null) {
            var multiplier = attack.attack.movement_speed();
            scaleMovement(input, multiplier);
            sprintTriggerTime = 0;
        }
    }

    @Unique
    private static void scaleMovement(ClientInput input, float multiplier) {
        var vector = input.getMoveVector();
        ((InputAccessor) input).spellEngine_setMovementVector(new Vec2(vector.x * multiplier, vector.y * multiplier));
    }
}
