package net.spell_engine.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import net.spell_damage.api.MagicSchool;
import net.spell_damage.api.enchantment.MagicalItemStack;
import net.spell_engine.api.spell.SpellContainer;
import net.spell_engine.internals.*;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin implements SpellCasterItemStack, MagicalItemStack {
    @Shadow public abstract Item getItem();

    @Shadow @Final @Deprecated private Item item;

    private ItemStack itemStack() {
        return (ItemStack) ((Object)this);
    }

    @Nullable
    private SpellContainer container() {
        var item = getItem();
        var id = Registry.ITEM.getId(item);
        return SpellRegistry.containerForItem(id);
    }

    // MagicalItemStack

    public @Nullable MagicSchool getMagicSchool() {
        var container = container();
        if (container != null) {
            return container.school;
        }
        return null;
    }

    // SpellCasterItemStack

    @Nullable
    public SpellContainer getSpellContainer() {
        return container();
    }

    // Use conditions

    @Inject(method = "isUsedOnRelease", at = @At("HEAD"), cancellable = true)
    private void isUsedOnRelease_HEAD(CallbackInfoReturnable<Boolean> cir) {
        if (container() == null) { return; }
        cir.setReturnValue(false);
        cir.cancel();
    }

    @Inject(method = "getMaxUseTime", at = @At("HEAD"), cancellable = true)
    private void getMaxUseTime_HEAD(CallbackInfoReturnable<Integer> cir) {
        if (container() == null) { return; }
        cir.setReturnValue(SpellHelper.maximumUseTicks);
        cir.cancel();
    }

    @Inject(method = "getUseAction", at = @At("HEAD"), cancellable = true)
    private void getUseAction_HEAD(CallbackInfoReturnable<UseAction> cir) {
        if (container() == null) { return; }
        cir.setReturnValue(UseAction.NONE);
        cir.cancel();
    }

    // Start casting

    // Can use runes?
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void use_HEAD(World world, PlayerEntity user, Hand hand, CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
        System.out.println("ItemStack use start");
        var itemStack = itemStack();
        var container = container();
        if (container == null) {
            if (user instanceof SpellCasterEntity caster && caster.getCurrentSpellId() != null) {
                if (world.isClient) {
                    var client = MinecraftClient.getInstance();
                    client.interactionManager.stopUsingItem(client.player);
                    caster.setCurrentSpell(null);
                } else {
                    SpellCastSyncHelper.clearCasting(user);
                }
            }
            return;
        }
        if (world.isClient) {
            if (user instanceof SpellCasterClient caster) {
                if (!caster.hasAmmoToStart(container, itemStack) || caster.isOnCooldown(container)) {
                    cir.setReturnValue(TypedActionResult.fail(itemStack()));
                    cir.cancel();
                    return;
                } else {
                    caster.castStart(container, itemStack, SpellHelper.maximumUseTicks);
                }
            }
        }
        cir.setReturnValue(TypedActionResult.consume(itemStack()));
        user.setCurrentHand(hand);
        cir.cancel();
    }

    // Tick cast

    @Inject(method = "usageTick", at = @At("HEAD"), cancellable = true)
    private void usageTick_HEAD(World world, LivingEntity user, int remainingUseTicks, CallbackInfo ci) {
        System.out.println("ItemStack use tick A " + (world.isClient ? "CLIENT" : "SERVER"));
        var spell = container();
        if (spell == null) {
            return;
        }
        System.out.println("ItemStack use tick B " + (world.isClient ? "CLIENT" : "SERVER"));

        if (user instanceof PlayerEntity player) {
            var caster = (SpellCasterEntity)player;
            if (caster.getCooldownManager().isCoolingDown(caster.getCurrentSpellId())) {
                var client = MinecraftClient.getInstance();
                client.interactionManager.stopUsingItem(client.player);
                // player.clearActiveItem();
                ci.cancel();
                return;
            }
        }

        if (world.isClient) {
            if (user instanceof SpellCasterClient caster) {
                caster.castTick(itemStack(), remainingUseTicks);
            }
        }

//        var progress = SpellHelper.getCastProgress(remainingUseTicks, spell.cast_duration);
//        System.out.println("Spell tick - Tick: " + progress);

        ci.cancel();
    }

    // Release casting

    // finishUsing = finish consuming runes

    @Inject(method = "onStoppedUsing", at = @At("HEAD"), cancellable = true)
    private void onStoppedUsing_HEAD(World world, LivingEntity user, int remainingUseTicks, CallbackInfo ci) {
        System.out.println("ItemStack use stop");
        var spell = container();
        if (spell == null) { return; }

        if (world.isClient) {
            if (user instanceof SpellCasterClient caster) {
                caster.castRelease(itemStack(), remainingUseTicks);
            }
        }

        ci.cancel();
    }
}
