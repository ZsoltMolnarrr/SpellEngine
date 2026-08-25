package net.spell_engine.mixin.client.render.item;

import net.minecraft.client.render.item.property.bool.UsingItemProperty;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.spell_engine.client.render.RangedWeaponCastAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/// `minecraft:using_item` is the outer gate of every bow/crossbow item model: it selects the
/// pulling branch at all. Spell casting is not vanilla item use, so without this the pull models
/// are never reached while a ranged weapon spell is cast.
@Mixin(UsingItemProperty.class)
public class UsingItemPropertyMixin {
    @Inject(method = "test", at = @At("HEAD"), cancellable = true)
    private void spellEngine_reportRangedWeaponCast(ItemStack stack, ClientWorld world, LivingEntity entity,
                                                    int seed, ItemDisplayContext displayContext,
                                                    CallbackInfoReturnable<Boolean> cir) {
        if (RangedWeaponCastAnimation.isAnimating(stack, entity)) {
            cir.setReturnValue(true);
        }
        // Otherwise: no cancel, vanilla decides.
    }
}
