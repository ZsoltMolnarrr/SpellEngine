package net.combatspells.mixin;

import net.combatspells.internals.SpellRegistry;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    @Shadow public abstract Item getItem();

    private ItemStack itemStack() {
        return (ItemStack) ((Object)this);
    }

    @Nullable
    private String spell() {
        var item = getItem();
        var id = Registry.ITEM.getId(item);
        return SpellRegistry.spells.get(id);
    }

    // Use conditions

    @Inject(method = "isUsedOnRelease", at = @At("HEAD"), cancellable = true)
    private void isUsedOnRelease_HEAD(CallbackInfoReturnable<Boolean> cir) {
        if (spell() == null) { return; }
        cir.setReturnValue(false);
        cir.cancel();
    }

    @Inject(method = "getMaxUseTime", at = @At("HEAD"), cancellable = true)
    private void getMaxUseTime_HEAD(CallbackInfoReturnable<Integer> cir) {
        if (spell() == null) { return; }
        cir.setReturnValue(72000);
        cir.cancel();
    }

    @Inject(method = "getUseAction", at = @At("HEAD"), cancellable = true)
    private void getUseAction_HEAD(CallbackInfoReturnable<UseAction> cir) {
        if (spell() == null) { return; }
        cir.setReturnValue(UseAction.BOW);
        cir.cancel();
    }

    // Start casting

    // Can use item?
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void use_HEAD(World world, PlayerEntity user, Hand hand, CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
        if (spell() == null) { return; }

//        cir.setReturnValue(TypedActionResult.success(itemStack(), false));
        user.setCurrentHand(hand);
        cir.setReturnValue(TypedActionResult.consume(itemStack()));
        cir.cancel();
        // Nothing to do?
        System.out.println("Spell casting - Start");
    }

    // Tick cast

    @Inject(method = "usageTick", at = @At("HEAD"), cancellable = true)
    private void usageTick_HEAD(World world, LivingEntity user, int remainingUseTicks, CallbackInfo ci) {
        if (spell() == null) { return; }

        System.out.println("Spell tick - Tick");
        ci.cancel();
    }

    // Release casting

    // finishUsing = finish consuming item

    @Inject(method = "onStoppedUsing", at = @At("HEAD"), cancellable = true)
    private void onStoppedUsing_HEAD(World world, LivingEntity user, int remainingUseTicks, CallbackInfo ci) {
        if (spell() == null) { return; }

        System.out.println("Spell release - Release");
        ci.cancel();
    }
}
