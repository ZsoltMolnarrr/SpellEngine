package net.spell_engine.fabric;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.entity.Entity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.util.Identifier;
import net.spell_engine.api.attachment.SyncedEntityData;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// `SyncedEntityData` over Fabric API's data attachments. `setAttached` on the server syncs to
/// the entity's trackers plus the entity itself when it is a player (`EntityMixin.fabric_syncChange`),
/// and skips the send when the value is unchanged (`Objects.equals`).
public final class FabricSyncedEntityData<T> implements SyncedEntityData<T> {
    private final AttachmentType<T> type;
    private final T defaultValue;

    public FabricSyncedEntityData(Identifier id, T defaultValue,
                                  PacketCodec<? super RegistryByteBuf, T> sync,
                                  @Nullable Codec<T> persistence) {
        this.defaultValue = defaultValue;
        this.type = AttachmentRegistry.create(id, builder -> {
            builder.initializer(() -> defaultValue);
            if (persistence != null) {
                builder.persistent(persistence);
            }
            builder.syncWith(sync, AttachmentSyncPredicate.all());
        });
    }

    @Override
    public Identifier id() {
        return type.identifier();
    }

    @Override
    public T get(Entity entity) {
        return ((AttachmentTarget) entity).getAttachedOrElse(type, defaultValue);
    }

    @Override
    public void set(Entity entity, T value) {
        // Skip no-op writes, including "default onto an entity that carries nothing": the loader
        // compares against the *stored* value (null when absent), so it would otherwise attach and
        // sync the default to every tracker — and a sync can reach a client that no longer has the
        // entity (Fabric then logs an "unknown target" warning per change).
        if (Objects.equals(get(entity), value)) { return; }
        ((AttachmentTarget) entity).setAttached(type, value);
    }
}
