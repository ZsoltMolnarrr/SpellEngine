package net.spell_engine.neoforge;

import com.mojang.serialization.Codec;
import net.minecraft.entity.Entity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.util.Identifier;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.spell_engine.api.attachment.SyncedEntityData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// `SyncedEntityData` over NeoForge data attachments. Types are built immediately (common init
/// runs before any registry event) and buffered until `RegisterEvent` reaches
/// `NeoForgeRegistries.Keys.ATTACHMENT_TYPES`. `setData` on the server syncs to the entity's
/// trackers plus the entity itself when it is a player (`AttachmentSync.syncEntityUpdate`).
public final class NeoForgeSyncedEntityData<T> implements SyncedEntityData<T> {
    private static final List<NeoForgeSyncedEntityData<?>> buffered = new ArrayList<>();

    private final Identifier id;
    private final AttachmentType<T> type;
    private final T defaultValue;

    public NeoForgeSyncedEntityData(Identifier id, T defaultValue,
                                    PacketCodec<? super RegistryByteBuf, T> sync,
                                    @Nullable Codec<T> persistence) {
        this.id = id;
        this.defaultValue = defaultValue;
        var builder = AttachmentType.builder(() -> defaultValue).sync(sync);
        if (persistence != null) {
            builder.serialize(persistence.fieldOf("value"));
        }
        this.type = builder.build();
        buffered.add(this);
    }

    public static void onRegister(RegisterEvent event) {
        event.register(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, helper -> {
            for (var data : buffered) {
                helper.register(data.id, data.type);
            }
        });
    }

    @Override
    public Identifier id() {
        return id;
    }

    @Override
    public T get(Entity entity) {
        // getExistingDataOrNull: `getData` would default-create AND sync the attachment on every first read.
        // Called through the interface so the reference doesn't name the NeoForge-patched Entity superclass.
        var value = ((IAttachmentHolder) entity).getExistingDataOrNull(type);
        return value != null ? value : defaultValue;
    }

    @Override
    public void set(Entity entity, T value) {
        // Skip no-op writes (see the Fabric counterpart): writing the default onto an entity that
        // carries nothing would attach and sync it for no observable change.
        if (Objects.equals(get(entity), value)) { return; }
        ((IAttachmentHolder) entity).setData(type, value);
    }
}
