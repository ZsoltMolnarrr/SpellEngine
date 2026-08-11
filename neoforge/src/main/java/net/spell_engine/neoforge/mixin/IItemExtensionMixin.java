package net.spell_engine.neoforge.mixin;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.neoforged.neoforge.common.extensions.IItemExtension;
import net.spell_engine.neoforge.PlatformEventsImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/// NeoForge's replacement for Fabric's `EnchantmentEvents.ALLOW_ENCHANTING`.
///
/// `IItemExtension#supportsEnchantment(ItemStack, Enchantment)` is the single chokepoint the anvil
/// (via `ItemStack#supportsEnchantment`) and the enchanting table (via `isPrimaryItemFor`) both funnel
/// through, and it has both the stack and the enchantment in hand. We consult the common
/// `PlatformEvents.onAllowEnchanting` callbacks (buffered in {@link PlatformEventsImpl}) and convert
/// their Spell Engine `TriState` into the boolean this method returns:
/// `ALLOW` → force-allow, `DENY` → forbid, `PASS` → leave the vanilla/other-mod result untouched.
///
/// `remap = false`: `supportsEnchantment` is a NeoForge-added method, so its name must not be remapped.
@Mixin(IItemExtension.class)
public interface IItemExtensionMixin {
    @Inject(
            method = "supportsEnchantment(Lnet/minecraft/item/ItemStack;Lnet/minecraft/registry/entry/RegistryEntry;)Z",
            at = @At("RETURN"),
            cancellable = true,
            remap = false)
    default void spellEngine$supportsEnchantment(ItemStack stack, RegistryEntry<Enchantment> enchantment, CallbackInfoReturnable<Boolean> cir) {
        switch (PlatformEventsImpl.evaluateAllowEnchanting(enchantment, stack)) {
            case ALLOW -> cir.setReturnValue(true);
            case DENY -> cir.setReturnValue(false);
            case PASS -> { }
        }
    }
}
