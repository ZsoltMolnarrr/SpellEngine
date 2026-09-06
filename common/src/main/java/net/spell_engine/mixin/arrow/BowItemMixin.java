package net.spell_engine.mixin.arrow;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.spell_engine.internals.casting.SpellCaster;
import net.spell_engine.internals.delivery.arrow.ArrowHelper;
import net.spell_engine.internals.delivery.arrow.ArrowShootContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/// 1.20.1 bow shooting is inline in `BowItem.onStoppedUsing` (no `RangedWeaponItem.shootAll`):
/// arrow perks / triggers hook the `World.spawnEntity` call, quiver consumption the `ItemStack.decrement` call.
@Mixin(BowItem.class)
public class BowItemMixin {
    @WrapOperation(method = "onStoppedUsing", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;spawnEntity(Lnet/minecraft/entity/Entity;)Z"))
    private boolean onStoppedUsing_wrap_spawnEntity(
            // Mixin parameters
            World world, Entity entity, Operation<Boolean> original,
            // Context parameters
            ItemStack stack, World world2, LivingEntity user, int remainingUseTicks) {
        var result = original.call(world, entity);
        if (entity instanceof ProjectileEntity projectile) {
            ArrowHelper.onArrowSpawned(projectile, user, stack);
            if (user instanceof SpellCaster.Player caster) {
                caster.setArrowShootContext(ArrowShootContext.empty());
            }
        }
        return result;
    }

    /// The projectile stack may come from a quiver-like container (see `PlayerEntityQuiverMixin`), in which
    /// case it is a detached copy: consume from the container instead of the copy.
    @WrapOperation(method = "onStoppedUsing", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;decrement(I)V"))
    private void onStoppedUsing_wrap_decrement(
            // Mixin parameters
            ItemStack projectileStack, int amount, Operation<Void> original,
            // Context parameters
            ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (user instanceof PlayerEntity player && !isInInventory(player, projectileStack)) {
            var taken = ArrowHelper.takeOne(player, projectileStack);
            if (taken != null) {
                return;
            }
        }
        original.call(projectileStack, amount);
    }

    private static boolean isInInventory(PlayerEntity player, ItemStack stack) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.size(); i++) {
            if (inventory.getStack(i) == stack) {
                return true;
            }
        }
        return false;
    }
}
