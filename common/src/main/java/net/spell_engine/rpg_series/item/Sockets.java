package net.spell_engine.rpg_series.item;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.spell_engine.Platform;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Built-in gem sockets per armor piece, applied as the `jewelry:sockets` default component.
/// Resolved by id through Jewelry's own codec, so no compile dependency on Jewelry; a no-op without it.
public record Sockets(int head, int chest, int legs, int feet, @Nullable Identifier type) {
    private static final Logger LOGGER = LoggerFactory.getLogger("SpellEngine/Sockets");
    private static final String JEWELRY = "jewelry";
    private static final Identifier COMPONENT_ID = Identifier.fromNamespaceAndPath(JEWELRY, "sockets");

    public static final Sockets NONE = new Sockets(0, 0, 0, 0, null);

    public static Sockets of(int head, int chest, int legs, int feet) {
        return new Sockets(head, chest, legs, feet, null);
    }

    public static Sockets all(int count) {
        return of(count, count, count, count);
    }

    /// Sockets of the given socket type, e.g. one defined by a `socket_type` data entry
    public Sockets typed(@Nullable Identifier type) {
        return new Sockets(head, chest, legs, feet, type);
    }

    /// Adds `count` empty sockets (of `type`, standard when null) as a default component of the item
    @SuppressWarnings("unchecked")
    public static void apply(Item.Properties settings, int count, @Nullable Identifier type) {
        if (count <= 0 || !Platform.util().isModLoaded(JEWELRY)) {
            return;
        }
        var componentType = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(COMPONENT_ID);
        if (componentType == null || componentType.codec() == null) {
            LOGGER.warn("Jewelry is loaded but component `{}` is not registered, skipping sockets", COMPONENT_ID);
            return;
        }
        var json = new JsonArray();
        for (int i = 0; i < count; i++) {
            var socket = new JsonObject();
            if (type != null) {
                socket.addProperty("type", type.toString());
            }
            json.add(socket);
        }
        componentType.codec().parse(JsonOps.INSTANCE, json)
                .ifSuccess(value -> settings.component((DataComponentType<Object>) componentType, value))
                .ifError(error -> LOGGER.warn("Failed to decode `{}` from {}: {}", COMPONENT_ID, json, error.message()));
    }
}
