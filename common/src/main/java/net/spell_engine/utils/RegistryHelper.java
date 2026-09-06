package net.spell_engine.utils;

import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

import java.util.Optional;

/// 1.20.1 has no `Registry#getEntry(Identifier)` (added in 1.21); this is that lookup.
public class RegistryHelper {
    public static <T> Optional<RegistryEntry.Reference<T>> getEntry(Registry<T> registry, Identifier id) {
        return registry.getEntry(RegistryKey.of(registry.getKey(), id));
    }
}
