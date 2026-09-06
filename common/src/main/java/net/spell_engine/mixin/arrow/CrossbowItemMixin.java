package net.spell_engine.mixin.arrow;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import net.spell_engine.internals.casting.SpellCaster;
import net.spell_engine.internals.delivery.arrow.ArrowHelper;
import net.spell_engine.internals.delivery.arrow.ArrowShootContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/// 1.20.1 crossbow shooting is `CrossbowItem.shootAll` → private static `shoot` (one call per projectile):
/// arrow perks / triggers hook the `World.spawnEntity` call in `shoot`; the shooter's arrow-shoot context is
/// cleared once after `shootAll`, so every multishot projectile receives it. Loading consumes from a
/// quiver-like container first (`loadProjectile`'s `split`).
@Mixin(CrossbowItem.class)
public class CrossbowItemMixin {
    @WrapOperation(method = "shoot", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;spawnEntity(Lnet/minecraft/entity/Entity;)Z"))
    private static boolean shoot_wrap_spawnEntity(
            // Mixin parameters
            World world, Entity entity, Operation<Boolean> original,
            // Context parameters
            World world2, LivingEntity shooter, Hand hand, ItemStack crossbow, ItemStack projectile,
            float soundPitch, boolean creative, float speed, float divergence, float simulated) {
        var result = original.call(world, entity);
        if (entity instanceof ProjectileEntity projectileEntity) {
            ArrowHelper.onArrowSpawned(projectileEntity, shooter, crossbow);
        }
        return result;
    }

    @Inject(method = "shootAll", at = @At("TAIL"))
    private static void shootAll_TAIL_SpellEngine(World world, LivingEntity entity, Hand hand, ItemStack stack, float speed, float divergence, CallbackInfo ci) {
        if (entity instanceof SpellCaster.Player caster) {
            caster.setArrowShootContext(ArrowShootContext.empty());
        }
    }

    @WrapOperation(method = "loadProjectile", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;split(I)Lnet/minecraft/item/ItemStack;"))
    private static ItemStack loadProjectile_wrap_split(
            // Mixin parameters
            ItemStack projectileStack, int amount, Operation<ItemStack> original,
            // Context parameters
            LivingEntity shooter, ItemStack crossbow, ItemStack projectile, boolean simulated, boolean creative) {
        if (shooter instanceof PlayerEntity player && amount == 1) {
            var taken = ArrowHelper.takeOne(player, projectileStack);
            if (taken != null) {
                return taken;
            }
        }
        return original.call(projectileStack, amount);
    }
}
