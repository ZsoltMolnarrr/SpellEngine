package net.spell_engine.mixin.arrow;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.RangedWeaponItem;
import net.minecraft.world.World;
import net.spell_engine.internals.SpellTriggers;
import net.spell_engine.internals.arrow.ArrowExtension;
import net.spell_engine.internals.casting.SpellCasterEntity;
import net.spell_engine.utils.WorldScheduler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RangedWeaponItem.class)
public class RangedWeaponItemMixin {

    @WrapOperation(method = "shootAll", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/RangedWeaponItem;createArrowEntity(Lnet/minecraft/world/World;Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemStack;Z)Lnet/minecraft/entity/projectile/ProjectileEntity;"))
    private ProjectileEntity shootAll_wrap_createArrowEntity(
            RangedWeaponItem instance, World world, LivingEntity shooter, ItemStack weaponStack, ItemStack projectileStack, boolean critical,
            Operation<ProjectileEntity> original) {
        var projectile = original.call(instance, world, shooter, weaponStack, projectileStack, critical);
        if (shooter instanceof PlayerEntity player
                && projectile instanceof ArrowExtension arrow) {
            var caster = (SpellCasterEntity) player;
            var activeSpellEntry = caster.getTemporaryActiveSpell();
            if (activeSpellEntry != null) {
                if (activeSpellEntry.value().arrow_perks != null) {
                    arrow.applyArrowPerks(activeSpellEntry);
                }
            }

            // Avoid arrow shot event listeners self triggering
            ((WorldScheduler)world).schedule(0, () -> {
                SpellTriggers.onArrowShot(arrow, player);
            });
            // SpellTriggers.onArrowShot(arrow, player);
        }
        return projectile;
    }
}
