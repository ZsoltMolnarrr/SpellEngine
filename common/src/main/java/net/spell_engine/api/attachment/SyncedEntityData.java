package net.spell_engine.api.attachment;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.spell_engine.Platform;
import org.jetbrains.annotations.Nullable;

/// A per-entity value that the server owns and every client that can see the entity mirrors.
/// Loader-neutral face of the native data attachments: Fabric's `fabric-data-attachment-api-v1`
/// and NeoForge's `AttachmentType`. Replaces mixin-registered `TrackedData` on vanilla entity
/// classes, which NeoForge rejects.
///
/// Sync contract (both loaders, verified against the API sources): a server-side {@link #set}
/// is sent to every player tracking the entity **and to the entity itself when it is a player**
/// (players do not track themselves; both loaders special-case this). Clients that start tracking
/// the entity, and players on login/respawn, receive the current value up front. Absent values
/// read as `defaultValue`, so nothing has to be initialised per entity.
///
/// Values should be immutable (primitives, records, `List.copyOf`): the loaders compare the old
/// and new value to decide whether to sync, and hand the same instance out on every read. Reads
/// are a single identity-map lookup — cheap enough for per-frame renderer use.
public interface SyncedEntityData<T> {
    Identifier id();

    /// The current value on either side, `defaultValue` when the entity carries none.
    T get(Entity entity);

    /// Stores the value; on the server this also syncs it (see the class comment). On the client it
    /// only updates the local mirror — legitimate for prediction, but the next server sync wins.
    void set(Entity entity, T value);

    /// Registers a synced entity attachment. Must be called during common init on both sides (the
    /// client has to know the type to accept the sync). Persisted with the entity only when a
    /// `persistence` codec is given; otherwise the value lives for the entity instance and is
    /// dropped on death/respawn, exactly like tracked data was.
    static <T> SyncedEntityData<T> create(Identifier id, T defaultValue,
                                          StreamCodec<? super RegistryFriendlyByteBuf, T> sync,
                                          @Nullable Codec<T> persistence) {
        return Platform.util().createSyncedEntityData(id, defaultValue, sync, persistence);
    }

    static <T> SyncedEntityData<T> create(Identifier id, T defaultValue, StreamCodec<? super RegistryFriendlyByteBuf, T> sync) {
        return create(id, defaultValue, sync, null);
    }
}
