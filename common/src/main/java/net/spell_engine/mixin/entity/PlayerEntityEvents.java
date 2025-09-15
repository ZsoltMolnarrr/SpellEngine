package net.spell_engine.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.spell_engine.api.event.CombatEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerEntity.class)
public class PlayerEntityEvents {
    @WrapOperation(
            method = "attack",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;onAttacking(Lnet/minecraft/entity/Entity;)V")
    )
    private void attack_WRAP_onAttacking(PlayerEntity instance, Entity entity, Operation<Void> original) {
        original.call(instance, entity);
        if (CombatEvents.PLAYER_MELEE_ATTACK.isListened()) {
            var args = new CombatEvents.PlayerAttack.Args(instance, entity);
            CombatEvents.PLAYER_MELEE_ATTACK.invoke(listener -> listener.onPlayerAttack(args));
        }
    }
}
