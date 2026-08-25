package net.spell_engine.mixin.client.control;

import org.spongepowered.asm.mixin.Unique;
import net.minecraft.util.math.Vec2f;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.internals.casting.SpellCaster;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public class SpellCastingMovement {
    @Shadow public Input input;
    @Shadow protected int ticksLeftToDoubleTapSprint;

    @Inject(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/input/Input;tick()V", shift = At.Shift.AFTER))
    private void tickMovement_ModifyInput(CallbackInfo ci) {
        var player = (ClientPlayerEntity) (Object) this;
        var caster = (SpellCaster.Client) player;
        var process = caster.getSpellCastProcess();
        if (process != null && process.spell().value().active.cast != null && !player.hasVehicle()) {
            var multiplier = process.spell().value().active.cast.movement_speed * SpellEngineMod.config.movement_multiplier_speed_while_casting;
            scaleMovement(input, multiplier);
            ticksLeftToDoubleTapSprint = 0;
        }
        var attack = caster.getCurrentSkillAttack();
        if (attack != null) {
            var multiplier = attack.attack.movement_speed();
            scaleMovement(input, multiplier);
            ticksLeftToDoubleTapSprint = 0;
        }
    }

    @Unique
    private static void scaleMovement(Input input, float multiplier) {
        var accessor = (InputAccessor) input;
        var vector = accessor.spellEngine_getMovementVector();
        accessor.spellEngine_setMovementVector(new Vec2f(vector.x * multiplier, vector.y * multiplier));
    }
}
