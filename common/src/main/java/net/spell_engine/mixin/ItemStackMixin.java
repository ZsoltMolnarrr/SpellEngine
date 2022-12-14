package net.spell_engine.mixin;

import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.SpellContainer;
import net.spell_engine.internals.SpellCasterClient;
import net.spell_engine.internals.SpellCasterItemStack;
import net.spell_engine.internals.SpellHelper;
import net.spell_engine.internals.SpellRegistry;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import net.spell_damage.api.MagicSchool;
import net.spell_damage.api.enchantment.MagicalItemStack;
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

    private SpellContainer cachedSpell;

    @Nullable
    private SpellContainer container() {
        if (cachedSpell != null) {
            return cachedSpell;
        }
        var item = getItem();
        var id = Registry.ITEM.getId(item);
        cachedSpell = SpellRegistry.containerForItem(id);
        return cachedSpell;
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
        var itemStack = itemStack();
        var container = container();
        if (container == null) { return; }
        if (world.isClient) {
            if (user instanceof SpellCasterClient caster) {
                if (!caster.hasAmmoToStart(container, itemStack)) {
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
        var spell = container();
        if (spell == null) { return; }

        if (world.isClient) {
            if (user instanceof PlayerEntity player) {
                if (player.getItemCooldownManager().isCoolingDown(itemStack().getItem())) {
                    player.clearActiveItem();
                }
            }
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
