package net.spell_engine.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.spell_engine.api.event.CombatEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityEvents {
    @Inject(method = "onAttacking", at = @At("HEAD"))
    private void onAttacking_HEAD_Event(Entity target, CallbackInfo ci) {
        var entity = (LivingEntity) (Object) this;
        if (CombatEvents.ENTITY_ANY_ATTACK.isListened()) {
            var args = new CombatEvents.EntityAttack.Args(entity, target);
            CombatEvents.ENTITY_ANY_ATTACK.invoke(listener -> listener.onEntityAttack(args));
        }
        // Spell impact damage execution does call back here (`onAttacking`)
        // so we need to avoid infinite loop
        if (entity instanceof PlayerEntity player) {
            if (CombatEvents.PLAYER_ANY_ATTACK.isListened()) {
                var args = new CombatEvents.PlayerAttack.Args(player, target);
                CombatEvents.PLAYER_ANY_ATTACK.invoke(listener -> listener.onPlayerAttack(args));
            }
        }
    }

    /// Logic moved into `LivingEntityHealthImpacting` mixin
    /// to avoid conflicting order
//    @WrapOperation(
//            method = "damage",
//            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;applyDamage(Lnet/minecraft/entity/damage/DamageSource;F)V")
//    )
//    private void damage_ApplyDamage_entity(
//            // Mixin parameters
//            LivingEntity instance, DamageSource source, float amount, Operation<Void> original
//    ) {
//        if (CombatEvents.ENTITY_DAMAGE_INCOMING.isListened()) {
//            var args = new CombatEvents.EntityDamageTaken.Args(instance, source, amount);
//            CombatEvents.ENTITY_DAMAGE_INCOMING.invoke(listener -> listener.onDamageTaken(args));
//        }
//        if (instance instanceof PlayerEntity player) {
//            if (CombatEvents.PLAYER_DAMAGE_INCOMING.isListened()) {
//                var args = new CombatEvents.PlayerDamageTaken.Args(player, source, amount);
//                CombatEvents.PLAYER_DAMAGE_INCOMING.invoke(listener -> listener.onPlayerDamageTaken(args));
//            }
//        }
//        original.call(instance, source, amount);
//    }

    @Inject(method = "damage", at = @At("RETURN"))
    private void damage_RETURN_entity(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            var entity = (LivingEntity) (Object) this;
            if (CombatEvents.ENTITY_DAMAGE_TAKEN.isListened()) {
                var args = new CombatEvents.EntityDamageTaken.Args(entity, source, amount);
                CombatEvents.ENTITY_DAMAGE_TAKEN.invoke(listener -> listener.onDamageTaken(args));
            }
            if (entity instanceof PlayerEntity player) {
                if (CombatEvents.PLAYER_DAMAGE_TAKEN.isListened()) {
                    var args = new CombatEvents.PlayerDamageTaken.Args(player, source, amount);
                    CombatEvents.PLAYER_DAMAGE_TAKEN.invoke(listener -> listener.onPlayerDamageTaken(args));
                }
            }
        }
    }

    @Inject(method = "tickItemStackUsage", at = @At("HEAD"))
    private void tickItemStackUsage_HEAD_Event(CallbackInfo ci) {
        var entity = (LivingEntity) (Object) this;
        if (CombatEvents.ITEM_USE.isListened()) {
            var args = new CombatEvents.ItemUse.Args(entity, CombatEvents.ItemUse.Stage.TICK);
            CombatEvents.ITEM_USE.invoke(listener -> listener.onItemUseStart(args));
        }
    }


    /**
     * `damageShield` is the first thing that is called when a shield block happens
     */
    @WrapOperation(
            method = "damage",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;damageShield(F)V")
    )
    private void damage_WRAP_damageShield(
            // Mixin parameters
            LivingEntity instance, float durabilityAmount, Operation<Void> original,
            // Context parameters
            DamageSource source, float damageAmount
    ) {
        // Seems like `damageAmount` and `durabilityAmount` are the same
        if (CombatEvents.ENTITY_SHIELD_BLOCK.isListened()) {
            var args = new CombatEvents.EntityShieldBlock.Args(instance, source, durabilityAmount);
            CombatEvents.ENTITY_SHIELD_BLOCK.invoke(listener -> listener.onShieldBlock(args));
        }
        if (instance instanceof PlayerEntity player) {
            if (CombatEvents.PLAYER_SHIELD_BLOCK.isListened()) {
                var args = new CombatEvents.PlayerShieldBlock.Args(player, source, durabilityAmount);
                CombatEvents.PLAYER_SHIELD_BLOCK.invoke(listener -> listener.onShieldBlock(args));
            }
        }
        original.call(instance, durabilityAmount);
    }
}
