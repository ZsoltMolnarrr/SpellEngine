package net.spell_engine.neoforge;

import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

import java.util.ArrayList;
import java.util.List;

/// Buffers synced datapack-registry definitions requested during common init and flushes them
/// into NeoForge's `DataPackRegistryEvent.NewRegistry` — the only point NeoForge accepts them.
/// Replaces Fabric API's imperative `DynamicRegistries.registerSynced`.
public class SyncedDataRegistrar {
    private record Entry<T>(ResourceKey<Registry<T>> key, Codec<T> localCodec, Codec<T> networkCodec) {
        void applyTo(DataPackRegistryEvent.NewRegistry event) {
            event.dataPackRegistry(key, localCodec, networkCodec);
        }
    }

    private static final List<Entry<?>> buffered = new ArrayList<>();

    public static <T> void buffer(ResourceKey<Registry<T>> key, Codec<T> localCodec, Codec<T> networkCodec) {
        buffered.add(new Entry<>(key, localCodec, networkCodec));
    }

    public static void onNewRegistry(DataPackRegistryEvent.NewRegistry event) {
        for (var entry : buffered) {
            entry.applyTo(event);
        }
    }
}
