package net.spell_engine.mixin.client.render.item;

import net.minecraft.client.render.item.property.numeric.UseDurationProperty;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.util.HeldItemContext;
import net.spell_engine.client.render.RangedWeaponCastAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/// `minecraft:use_duration` is what vanilla's own `bow.json` (and most third-party bows) dispatch
/// the pull frames on. Reported in ticks, scaled by the model's `scale` — so the cast ratio is
/// mapped back onto vanilla's 20-tick draw assumption rather than the spell's real cast length.
@Mixin(UseDurationProperty.class)
public class UseDurationPropertyMixin {
    @Inject(method = "getValue", at = @At("HEAD"), cancellable = true)
    private void spellEngine_reportRangedWeaponCast(ItemStack stack, ClientWorld world, HeldItemContext context,
                                                    int seed, CallbackInfoReturnable<Float> cir) {
        var entity = context != null ? context.getEntity() : null;
        var ratio = RangedWeaponCastAnimation.pullRatio(stack, entity);
        if (ratio == null) {
            return;
        }
        // `remaining` inverts the reading (ticks left rather than ticks spent).
        var remaining = ((UseDurationProperty) (Object) this).remaining();
        var reported = remaining ? (1F - ratio) : ratio;
        cir.setReturnValue(reported * RangedWeaponCastAnimation.VANILLA_PULL_TICKS);
    }
}
