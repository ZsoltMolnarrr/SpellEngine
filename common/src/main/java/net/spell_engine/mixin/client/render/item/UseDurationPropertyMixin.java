package net.spell_engine.mixin.client.render.item;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.UseDuration;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;
import net.spell_engine.client.render.RangedWeaponCastAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/// `minecraft:use_duration` is what vanilla's own `bow.json` (and most third-party bows) dispatch
/// the pull frames on. Reported in ticks, scaled by the model's `scale` — so the cast ratio is
/// mapped back onto vanilla's 20-tick draw assumption rather than the spell's real cast length.
@Mixin(UseDuration.class)
public class UseDurationPropertyMixin {
    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private void spellEngine_reportRangedWeaponCast(ItemStack stack, ClientLevel world, ItemOwner context,
                                                    int seed, CallbackInfoReturnable<Float> cir) {
        var entity = context != null ? context.asLivingEntity() : null;
        var ratio = RangedWeaponCastAnimation.pullRatio(stack, entity);
        if (ratio == null) {
            return;
        }
        // `remaining` inverts the reading (ticks left rather than ticks spent).
        var remaining = ((UseDuration) (Object) this).remaining();
        var reported = remaining ? (1F - ratio) : ratio;
        cir.setReturnValue(reported * RangedWeaponCastAnimation.VANILLA_PULL_TICKS);
    }
}
