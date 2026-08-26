package net.spell_engine.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.level.Level;
import net.spell_engine.api.event.CombatEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityEvents {
    @Inject(method = "setLastHurtMob", at = @At("HEAD"))
    private void onAttacking_HEAD_Event(Entity target, CallbackInfo ci) {
        var entity = (LivingEntity) (Object) this;
        if (CombatEvents.ENTITY_ANY_ATTACK.isListened()) {
            var args = new CombatEvents.EntityAttack.Args(entity, target);
            CombatEvents.ENTITY_ANY_ATTACK.invoke(listener -> listener.onEntityAttack(args));
        }
        // Spell impact damage execution does call back here (`onAttacking`)
        // so we need to avoid infinite loop
        if (entity instanceof Player player) {
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

    @Inject(method = "hurtServer", at = @At("RETURN"))
    private void damage_RETURN_entity(ServerLevel world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            var entity = (LivingEntity) (Object) this;
            if (CombatEvents.ENTITY_DAMAGE_TAKEN.isListened()) {
                var args = new CombatEvents.EntityDamageTaken.Args(entity, source, amount);
                CombatEvents.ENTITY_DAMAGE_TAKEN.invoke(listener -> listener.onDamageTaken(args));
            }
            if (entity instanceof Player player) {
                if (CombatEvents.PLAYER_DAMAGE_TAKEN.isListened()) {
                    var args = new CombatEvents.PlayerDamageTaken.Args(player, source, amount);
                    CombatEvents.PLAYER_DAMAGE_TAKEN.invoke(listener -> listener.onPlayerDamageTaken(args));
                }
            }
        }
    }

    @Inject(method = "updateUsingItem", at = @At("HEAD"))
    private void tickItemStackUsage_HEAD_Event(ItemStack stack, CallbackInfo ci) {
        var entity = (LivingEntity) (Object) this;
        if (CombatEvents.ITEM_USE.isListened()) {
            var args = new CombatEvents.ItemUse.Args(entity, CombatEvents.ItemUse.Stage.TICK);
            CombatEvents.ITEM_USE.invoke(listener -> listener.onItemUseStart(args));
        }
    }


    /**
     * 1.21.11: shield blocking moved into `getDamageBlockedAmount`, and the durability hit is
     * `BlocksAttacksComponent.onShieldHit(...)` — the first thing called once a block is confirmed.
     *
     * Fabric only (`require = 0`): NeoForge patches this call with an extra `shieldDamage` argument, so the
     * descriptor never matches there; the NeoForge module fires the same CombatEvents from
     * `LivingShieldBlockEvent` instead (see `PlatformEventsImpl`).
     */
    @WrapOperation(
            method = "applyItemBlocking",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/component/BlocksAttacks;hurtBlockingItem(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/InteractionHand;F)V"),
            require = 0
    )
    private void getDamageBlockedAmount_WRAP_onShieldHit(
            // Mixin parameters
            BlocksAttacks component, Level world, ItemStack stack, LivingEntity instance, InteractionHand hand, float blockedAmount, Operation<Void> original,
            // Context parameters
            ServerLevel serverWorld, DamageSource source, float damageAmount
    ) {
        if (CombatEvents.ENTITY_SHIELD_BLOCK.isListened()) {
            var args = new CombatEvents.EntityShieldBlock.Args(instance, source, blockedAmount);
            CombatEvents.ENTITY_SHIELD_BLOCK.invoke(listener -> listener.onShieldBlock(args));
        }
        if (instance instanceof Player player) {
            if (CombatEvents.PLAYER_SHIELD_BLOCK.isListened()) {
                var args = new CombatEvents.PlayerShieldBlock.Args(player, source, blockedAmount);
                CombatEvents.PLAYER_SHIELD_BLOCK.invoke(listener -> listener.onShieldBlock(args));
            }
        }
        original.call(component, world, stack, instance, hand, blockedAmount);
    }
}
