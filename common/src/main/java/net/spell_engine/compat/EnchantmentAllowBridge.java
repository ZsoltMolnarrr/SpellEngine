package net.spell_engine.compat;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.spell_engine.PlatformEvents;
import net.spell_engine.api.util.TriState;
import net.spell_power.api.enchantment.EnchantmentRestriction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/// Loader-neutral implementation of {@link PlatformEvents#onAllowEnchanting} for 1.20.1.
///
/// Neither loader offers a global "may this enchantment go on this stack" hook on 1.20.1 (Fabric API 0.92
/// has no `EnchantmentEvents`; Forge 47 only has the per-item `IForgeItem#canApplyAtEnchantingTable`).
/// Spell Power 1.6.0 already gates both the anvil/enchanted-book path (`Enchantment#isAcceptableItem` HEAD)
/// and the enchanting table (`EnchantmentHelper#getPossibleEntries` RETURN) on both loaders through
/// {@link EnchantmentRestriction}, so the Spell Engine callbacks are bridged into that registry instead of
/// adding a mixin of our own.
///
/// `EnchantmentRestriction` is keyed per enchantment while the Spell Engine callback is generic, so one
/// permit + one prohibit condition is installed for every registered enchantment; the conditions consult
/// the live callback list, so callbacks registered after installation are still honoured. Enchantments
/// registered after the first install are picked up by the loader-specific late hooks
/// (Fabric: `RegistryEntryAddedCallback`; Forge: `FMLCommonSetupEvent`, after every `RegisterEvent`).
public final class EnchantmentAllowBridge {
    private static final List<PlatformEvents.AllowEnchanting> callbacks = new ArrayList<>();
    private static final Set<Enchantment> installed = Collections.newSetFromMap(new IdentityHashMap<>());

    private EnchantmentAllowBridge() { }

    /// Buffers the callback and (re)installs conditions for everything currently registered.
    public static void register(PlatformEvents.AllowEnchanting callback) {
        callbacks.add(callback);
        installAll();
    }

    /// Installs conditions for every enchantment in the registry that has none yet. Idempotent; safe to call
    /// before registration is complete (the registry is only iterated, never written).
    public static void installAll() {
        for (var enchantment : Registries.ENCHANTMENT) {
            install(enchantment);
        }
    }

    /// Installs the permit/prohibit pair for one enchantment (no-op if already installed).
    public static void install(Enchantment enchantment) {
        if (!installed.add(enchantment)) {
            return;
        }
        EnchantmentRestriction.permit(enchantment, stack -> evaluate(enchantment, stack) == TriState.ALLOW);
        EnchantmentRestriction.prohibit(enchantment, stack -> evaluate(enchantment, stack) == TriState.DENY);
    }

    /// Combined decision of all callbacks. DENY wins over ALLOW; both win over PASS.
    public static TriState evaluate(Enchantment enchantment, ItemStack stack) {
        if (callbacks.isEmpty()) {
            return TriState.PASS;
        }
        return evaluate(Registries.ENCHANTMENT.getEntry(enchantment), stack);
    }

    public static TriState evaluate(RegistryEntry<Enchantment> enchantment, ItemStack stack) {
        var result = TriState.PASS;
        for (var callback : callbacks) {
            switch (callback.allow(enchantment, stack)) {
                case DENY -> { return TriState.DENY; }
                case ALLOW -> result = TriState.ALLOW;
                case PASS -> { }
            }
        }
        return result;
    }
}
