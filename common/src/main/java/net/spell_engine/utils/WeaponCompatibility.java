package net.spell_engine.utils;

import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.spell.container.SpellContainer;
import net.spell_engine.config.FallbackConfig;
import net.spell_engine.internals.container.SpellAssignments;

public class WeaponCompatibility {
    public static void initialize() {
        var config = SpellEngineMod.fallbackConfig.safeValue();
        if (!config.enabled) {
            return;
        }

        // Process all items in the registry
        for (var itemId : Registries.ITEM.getIds()) {
            var item = Registries.ITEM.get(itemId);
            var itemEntry = item.getRegistryEntry();

            // Try melee weapons group
            if (config.melee_weapons.enabled &&
                    (item instanceof SwordItem || item instanceof TridentItem || item instanceof MaceItem || item instanceof AxeItem) ) {
                SpellContainer container = processCompatGroup(
                        itemEntry,
                        config.melee_weapons
                );
                if (container != null) {
                    SpellAssignments.containers.putIfAbsent(itemId, container);
                    continue; // Don't process other groups
                }
            }

            // Try ranged weapons group
            if (config.ranged_weapons.enabled &&
                (item instanceof RangedWeaponItem) ) {
                SpellContainer container = processCompatGroup(
                        itemEntry,
                        config.ranged_weapons
                );
                if (container != null) {
                    SpellAssignments.containers.putIfAbsent(itemId, container);
                    continue; // Don't process other groups
                }
            }
        }
    }

    private static SpellContainer processCompatGroup(
            RegistryEntry<Item> itemEntry,
            FallbackConfig.CompatGroup group) {

        // Check blacklist
        if (group.blacklist != null && !group.blacklist.isEmpty()) {
            if (PatternMatching.matches(itemEntry, RegistryKeys.ITEM, group.blacklist)) {
                return null; // Item is blacklisted
            }
        }

        // Try to match against specifiers in order
        if (group.enable_specifiers) {
            for (var specifier : group.specifiers) {
                if (specifier.item != null && !specifier.item.isEmpty()) {
                    if (PatternMatching.matches(itemEntry, RegistryKeys.ITEM, specifier.item)) {
                        return specifier.container; // First match wins
                    }
                }
            }
        }

        // No specifier matched, use default
        return group.defaults;
    }
}
