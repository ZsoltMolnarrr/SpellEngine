package net.combatspells.mixin;

import net.combatspells.api.SpellHelper;
import net.combatspells.api.spell.Spell;
import net.combatspells.internals.SpellCasterClient;
import net.combatspells.internals.SpellCasterItemStack;
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
public abstract class ItemStackMixin implements SpellCasterItemStack {
    @Shadow public abstract Item getItem();

    private ItemStack itemStack() {
        return (ItemStack) ((Object)this);
    }

    private Spell cachedSpell;

    @Nullable
    private Spell spell() {
        if (cachedSpell != null) {
            return cachedSpell;
        }
        var item = getItem();
        var id = Registry.ITEM.getId(item);
        cachedSpell = SpellRegistry.spells.get(id);
        return cachedSpell;
    }

    // SpellCasterItemStack
    @Override
    public Spell getSpell() {
        return spell();
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
        cir.setReturnValue(SpellHelper.maximumUseTicks);
        cir.cancel();
    }

    @Inject(method = "getUseAction", at = @At("HEAD"), cancellable = true)
    private void getUseAction_HEAD(CallbackInfoReturnable<UseAction> cir) {
        if (spell() == null) { return; }
        cir.setReturnValue(UseAction.NONE);
        cir.cancel();
    }

    // Start casting

    // Can use item?
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void use_HEAD(World world, PlayerEntity user, Hand hand, CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
        var spell = spell();
        if (spell() == null) { return; }
        if (SpellHelper.ammoForSpell(user, spell, itemStack()).satisfied()) {
            if (world.isClient) {
                if (user instanceof SpellCasterClient caster) {
                    caster.castStart(spell);
                }
            }
            user.setCurrentHand(hand); // Set item in use
            cir.setReturnValue(TypedActionResult.consume(itemStack()));
        } else {
            cir.setReturnValue(TypedActionResult.fail(itemStack()));
        }
        cir.cancel();
    }

    // Tick cast

    @Inject(method = "usageTick", at = @At("HEAD"), cancellable = true)
    private void usageTick_HEAD(World world, LivingEntity user, int remainingUseTicks, CallbackInfo ci) {
        var spell = spell();
        if (spell == null) { return; }

        if (world.isClient) {
            if (user instanceof SpellCasterClient caster) {
                caster.castTick(remainingUseTicks);
            }
        }

//        var progress = SpellHelper.getCastProgress(remainingUseTicks, spell.cast_duration);
//        System.out.println("Spell tick - Tick: " + progress);

        ci.cancel();
    }

    // Release casting

    // finishUsing = finish consuming item

    @Inject(method = "onStoppedUsing", at = @At("HEAD"), cancellable = true)
    private void onStoppedUsing_HEAD(World world, LivingEntity user, int remainingUseTicks, CallbackInfo ci) {
        var spell = spell();
        if (spell == null) { return; }

        if (world.isClient) {
            if (user instanceof SpellCasterClient caster) {
                caster.castRelease(itemStack(), remainingUseTicks);
            }
        }

        ci.cancel();
    }
}
