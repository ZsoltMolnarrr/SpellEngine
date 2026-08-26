package net.spell_engine.utils;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.TridentItem;
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
        for (var itemId : BuiltInRegistries.ITEM.keySet()) {
            var item = BuiltInRegistries.ITEM.getValue(itemId);
            var itemEntry = item.builtInRegistryHolder();

            // Try melee weapons group
            if (config.melee_weapons.enabled &&
                    (item.components().get(DataComponents.WEAPON) != null /* not contains(): Yarn/NeoForge name mismatch */ || item instanceof TridentItem || item instanceof MaceItem || item instanceof AxeItem) ) {
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
                (item instanceof ProjectileWeaponItem) ) {
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
            Holder<Item> itemEntry,
            FallbackConfig.CompatGroup group) {

        // Check blacklist
        if (group.blacklist != null && !group.blacklist.isEmpty()) {
            if (PatternMatching.matches(itemEntry, Registries.ITEM, group.blacklist)) {
                return null; // Item is blacklisted
            }
        }

        // Try to match against specifiers in order
        if (group.enable_specifiers) {
            for (var specifier : group.specifiers) {
                if (specifier.item != null && !specifier.item.isEmpty()) {
                    if (PatternMatching.matches(itemEntry, Registries.ITEM, specifier.item)) {
                        return specifier.container; // First match wins
                    }
                }
            }
        }

        // No specifier matched, use default
        return group.defaults;
    }
}
