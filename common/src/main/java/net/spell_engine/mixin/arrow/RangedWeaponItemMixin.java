package net.spell_engine.mixin.arrow;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.Level;
import net.spell_engine.Platform;

import com.google.common.base.Suppliers;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.spell_engine.internals.SpellTriggers;
import net.spell_engine.internals.delivery.arrow.ArrowExtension;
import net.spell_engine.internals.delivery.arrow.ArrowHelper;
import net.spell_engine.internals.delivery.arrow.ArrowShootContext;
import net.spell_engine.internals.casting.SpellCaster;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ProjectileWeaponItem.class)
public class RangedWeaponItemMixin {

    @WrapOperation(method = "shoot", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ProjectileWeaponItem;createProjectile(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/entity/projectile/Projectile;"))
    private Projectile shootAll_wrap_createArrowEntity(
            ProjectileWeaponItem instance, Level world, LivingEntity shooter, ItemStack weaponStack, ItemStack projectileStack, boolean critical,
            Operation<Projectile> original) {
        var projectile = original.call(instance, world, shooter, weaponStack, projectileStack, critical);
        if (shooter instanceof Player player
                && projectile instanceof ArrowExtension arrow) {
            var caster = (SpellCaster.Player) player;
            var shotContext = caster.getArrowShootContext();

            // First run triggers to enable modifying the arrow by passive spells
            // (by appending the arrow shot context)

            final var firedBySpell = shotContext.firedBySpell;
            SpellTriggers.onArrowShot(arrow, player, firedBySpell);

            // Apply arrow modification

            var trackers = Suppliers.memoize(() -> Platform.tracking(shooter));
            for (var spellEntry: shotContext.activeSpells) {
                ArrowHelper.onArrowShot(arrow, shooter, spellEntry, trackers);
            }

            // Clear arrow shoot context
            caster.setArrowShootContext(ArrowShootContext.empty());
        }
        return projectile;
    }
}
