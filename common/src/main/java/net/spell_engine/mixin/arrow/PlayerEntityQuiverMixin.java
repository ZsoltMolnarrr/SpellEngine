package net.spell_engine.mixin.arrow;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.spell_engine.internals.cost.Ammo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Predicate;

@Mixin(Player.class)
public class PlayerEntityQuiverMixin {
    @WrapOperation(method = "getProjectile", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ProjectileWeaponItem;getHeldProjectile(Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Predicate;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack getProjectileType_wrap_getHeldProjectile(
            // Mixin parameters
            LivingEntity entity, Predicate<ItemStack> predicate, Operation<ItemStack> original) {
        var stack = original.call(entity, predicate);
        if (stack.isEmpty()) {
            var player = (Player) entity;
            var container = Ammo.findContainer(player, predicate, 1);
            if (container != null) {
                stack = Ammo.findFirstInContainer(container.itemStack(), predicate);
            }
        }
        return stack;
    }
}
