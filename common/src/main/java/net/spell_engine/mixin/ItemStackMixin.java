package net.spell_engine.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.effect.EntityActionsAllowed;
import net.spell_engine.api.spell.SpellContainer;
import net.spell_engine.internals.*;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin implements SpellCasterItemStack {
    @Shadow public abstract Item getItem();

    private ItemStack itemStack() {
        return (ItemStack) ((Object)this);
    }

    @Nullable
    private SpellContainer spellContainer() {
        var nbtContainer = spellContainerFromNBT();
        if (nbtContainer != null) {
            return nbtContainer;
        }
        return spellContainerDefault();
    }

    @Nullable
    private SpellContainer spellContainerFromNBT() {
        var itemStack = itemStack();
        if (!itemStack.hasNbt()) {
            return null;
        }
        return SpellContainerHelper.fromNBT(itemStack.getNbt());
    }

    @Nullable
    private SpellContainer spellContainerDefault() {
        var item = getItem();
        var id = Registry.ITEM.getId(item);
        return SpellRegistry.containerForItem(id);
    }

    // SpellCasterItemStack

    @Nullable
    public SpellContainer getSpellContainer() {
        return spellContainer();
    }

    // Use conditions

    @Inject(method = "isUsedOnRelease", at = @At("HEAD"), cancellable = true)
    private void isUsedOnRelease_HEAD_SpellEngine(CallbackInfoReturnable<Boolean> cir) {
        if (spellContainer() == null) { return; }
        // This would make the `useTick` function called upon release
        // The problem is, there is no way to distinguish inside `useTick` where it was called from
        // Hence this is not used.
        cir.setReturnValue(false);
        cir.cancel();
    }

    @Inject(method = "getMaxUseTime", at = @At("HEAD"), cancellable = true)
    private void getMaxUseTime_HEAD_SpellEngine(CallbackInfoReturnable<Integer> cir) {
        if (spellContainer() == null) { return; }
        cir.setReturnValue(SpellHelper.maximumUseTicks);
        cir.cancel();
    }

    @Inject(method = "getUseAction", at = @At("HEAD"), cancellable = true)
    private void getUseAction_HEAD_SpellEngine(CallbackInfoReturnable<UseAction> cir) {
        if (spellContainer() == null) { return; }
        cir.setReturnValue(UseAction.NONE);
        cir.cancel();
    }

    // Start casting

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void use_HEAD_SpellEngine(World world, PlayerEntity user, Hand hand, CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
        System.out.println("ItemStack use start " + (world.isClient ? "CLIENT" : "SERVER") + " | time: " + user.age);
        if (hand == Hand.OFF_HAND && !SpellEngineMod.config.offhand_casting_allowed) {
            return;
        }
        var itemStack = itemStack();
        var container = spellContainer();
        if (container == null || !container.isUsable()) {
            if (user instanceof SpellCasterEntity caster && caster.getCurrentSpellId() != null) {
                caster.clearCasting();
            }
            return;
        }

        if (EntityActionsAllowed.isImpaired(user, EntityActionsAllowed.Player.CAST_SPELL, true)) {
            cir.setReturnValue(TypedActionResult.fail(itemStack()));
            cir.cancel();
            return;
        }

        var attempt = SpellCast.Attempt.none();
        if (world.isClient) {
            if (user instanceof SpellCasterClient caster) {
                if (hand == Hand.MAIN_HAND
                        && !user.getOffHandStack().isEmpty()
                        && caster.isHotbarModifierPressed()) {
                    return; // Allow offhand item to work
                }
                var spellId = caster.getSelectedSpellId(container);
                attempt = SpellHelper.attemptCasting(user, itemStack, spellId);
                caster.castAttempt(attempt);
                if (attempt.isSuccess()) {
                    caster.castStart(container, hand, itemStack, SpellHelper.maximumUseTicks);
                }
            }
        } else {
            var spellId = ((SpellCasterEntity)user).getCurrentSpellId();
            if (spellId != null) {
                attempt = SpellHelper.attemptCasting(user, itemStack, spellId);
            }
        }

        if (attempt.isSuccess()) {
            cir.setReturnValue(TypedActionResult.consume(itemStack()));
            user.setCurrentHand(hand);
        } else if (attempt.isFail()) {
            cir.setReturnValue(TypedActionResult.fail(itemStack()));
        } else {
            cir.setReturnValue(TypedActionResult.pass(itemStack()));
        }
        cir.cancel();
    }

    // Tick cast

    @Inject(method = "usageTick", at = @At("HEAD"), cancellable = true)
    private void usageTick_HEAD_SpellEngine(World world, LivingEntity user, int remainingUseTicks, CallbackInfo ci) {
        // System.out.println("ItemStack use tick " + (world.isClient ? "CLIENT" : "SERVER") + " | time: " + user.age);
        var spell = spellContainer();
        if (spell == null) {
            return;
        }

        if (user instanceof PlayerEntity player) {
            var caster = (SpellCasterEntity)player;
            var attempt = SpellHelper.attemptCasting(player, itemStack(), caster.getCurrentSpellId());
            if (attempt.isSuccess()) {
                if (world.isClient) {
                    if (user instanceof SpellCasterClient casterClient) {
                        casterClient.castTick(itemStack(), user.getActiveHand(), remainingUseTicks);
                    }
                }
            } else {
                player.stopUsingItem();
            }
        }

        ci.cancel();
    }

    // Release casting

    @Inject(method = "onStoppedUsing", at = @At("HEAD"), cancellable = true)
    private void onStoppedUsing_HEAD_SpellEngine(World world, LivingEntity user, int remainingUseTicks, CallbackInfo ci) {
        // System.out.println("ItemStack use stop "  + (world.isClient ? "CLIENT" : "SERVER") + " | time: " + user.age);
        var spell = spellContainer();
        if (spell == null) { return; }

        if (world.isClient) {
            if (user instanceof SpellCasterClient caster) {
                // WATCH OUT `LivingEntity.clearActiveItem` also calls `castRelease`
                // using a mixin to the method `HEAD`
                // This is to make sure the spell release is released even if switching to another item.
                caster.castRelease(itemStack(), remainingUseTicks);
            }
        }

        ci.cancel();
    }
}