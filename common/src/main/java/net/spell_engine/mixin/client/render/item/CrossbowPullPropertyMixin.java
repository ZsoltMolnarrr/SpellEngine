package net.spell_engine.mixin.client.render.item;

import net.minecraft.client.render.item.property.numeric.CrossbowPullProperty;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.util.HeldItemContext;
import net.spell_engine.client.render.RangedWeaponCastAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/// `minecraft:crossbow/pull` is the crossbow counterpart of `minecraft:use_duration`, and already
/// speaks in `[0, 1]` — no rescaling needed. Note vanilla's `crossbow.json` gates this behind a
/// `minecraft:charge_type` select, so an already-charged crossbow keeps rendering its loaded model.
@Mixin(CrossbowPullProperty.class)
public class CrossbowPullPropertyMixin {
    @Inject(method = "getValue", at = @At("HEAD"), cancellable = true)
    private void spellEngine_reportRangedWeaponCast(ItemStack stack, ClientWorld world, HeldItemContext context,
                                                    int seed, CallbackInfoReturnable<Float> cir) {
        var entity = context != null ? context.getEntity() : null;
        var ratio = RangedWeaponCastAnimation.pullRatio(stack, entity);
        if (ratio != null) {
            cir.setReturnValue(ratio);
        }
    }
}
