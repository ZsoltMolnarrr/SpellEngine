package net.spell_engine.neoforge;

import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.NewDatapackRegistryEvent;

import java.util.ArrayList;
import java.util.List;

/// Buffers synced datapack-registry definitions requested during common init and flushes them
/// into NeoForge's `NewDatapackRegistryEvent` (world registries) — the only point NeoForge accepts them.
/// Replaces Fabric API's imperative `DynamicRegistries.registerSynced`.
public class SyncedDataRegistrar {
    private record Entry<T>(ResourceKey<Registry<T>> key, Codec<T> localCodec, Codec<T> networkCodec) {
        void applyTo(NewDatapackRegistryEvent event) {
            if (networkCodec != null) {
                event.worldRegistry(key, localCodec, networkCodec);
            } else {
                event.worldRegistry(key, localCodec);
            }
        }
    }

    private static final List<Entry<?>> buffered = new ArrayList<>();

    public static <T> void buffer(ResourceKey<Registry<T>> key, Codec<T> localCodec, Codec<T> networkCodec) {
        buffered.add(new Entry<>(key, localCodec, networkCodec));
    }

    public static void onNewRegistry(NewDatapackRegistryEvent event) {
        for (var entry : buffered) {
            entry.applyTo(event);
        }
    }
}
