package net.spell_engine.mixin.arrow;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.spell_engine.internals.cost.Ammo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ProjectileWeaponItem.class)
public class RangedWeaponQuiverMixin {

    /**
     * Wrap projectileStack.split(amount)
     * to remove arrow from quiver instead
     */

    @WrapOperation(
            method = "useAmmo",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;split(I)Lnet/minecraft/world/item/ItemStack;"))
    private static ItemStack spell_engine_getProjectile_wrap_split(
            // Mixin parameters
            ItemStack projectileStack, int amount, Operation<ItemStack> original,
            // Context parameters
            ItemStack stack, ItemStack projectileStack2, LivingEntity shooter, boolean multishot
    ) {
        int takenFromContainer = 0;
        if (shooter instanceof Player player) {
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
