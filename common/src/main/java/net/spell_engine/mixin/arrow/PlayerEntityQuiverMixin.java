package net.spell_engine.mixin.arrow;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.spell_engine.internals.Ammo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Predicate;

@Mixin(PlayerEntity.class)
public class PlayerEntityQuiverMixin {
    @WrapOperation(method = "getProjectileType", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/RangedWeaponItem;getHeldProjectile(Lnet/minecraft/entity/LivingEntity;Ljava/util/function/Predicate;)Lnet/minecraft/item/ItemStack;"))
    private ItemStack getProjectileType_wrap_getHeldProjectile(
            // Mixin parameters
            LivingEntity entity, Predicate<ItemStack> predicate, Operation<ItemStack> original) {
        var stack = original.call(entity, predicate);
        if (stack.isEmpty()) {
            var player = (PlayerEntity) entity;
            var container = Ammo.findContainer(player, predicate, 1);
            if (container != null) {
                stack = Ammo.findFirstInContainer(container.itemStack(), predicate);
            }
        }
        return stack;
    }
}
