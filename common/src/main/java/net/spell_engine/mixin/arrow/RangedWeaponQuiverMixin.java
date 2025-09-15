package net.spell_engine.mixin.arrow;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.RangedWeaponItem;
import net.spell_engine.internals.Ammo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RangedWeaponItem.class)
public class RangedWeaponQuiverMixin {

    /**
     * Wrap projectileStack.split(amount)
     * to remove arrow from quiver instead
     */

    @WrapOperation(
            method = "getProjectile",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;split(I)Lnet/minecraft/item/ItemStack;"))
    private static ItemStack spell_engine_getProjectile_wrap_split(
            // Mixin parameters
            ItemStack projectileStack, int amount, Operation<ItemStack> original,
            // Context parameters
            ItemStack stack, ItemStack projectileStack2, LivingEntity shooter, boolean multishot
    ) {
        int takenFromContainer = 0;
        if (shooter instanceof PlayerEntity player) {
            var item = projectileStack.getItem();
            var asd = projectileStack.copy();
            var predicate = new Ammo.Searched(null, item).asPredicate();
            var source = Ammo.findContainer(player, predicate, amount);
            if (source != null) {
                takenFromContainer = Ammo.takeFromContainer(source.itemStack(), predicate, amount);
            }
            asd.setCount(takenFromContainer);
            if (takenFromContainer == amount) {
                return asd;
            }
        }
        return original.call(projectileStack, amount - takenFromContainer);
    }
}
