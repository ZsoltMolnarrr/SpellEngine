package net.spell_engine.api.spell.registry;

import com.google.gson.*;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.Level;
import net.spell_engine.api.spell.Spell;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

public class SpellRegistry {
    /**
     * Using vanilla name space on purpose!
     * So spell data file path looks like this:
     * `data/MOD/spell/SPELL.json`
     * instead of this:
     * `data/MOD/spell_engine/spell/SPELL.json`
     */
    public static final Identifier ID = Identifier.withDefaultNamespace("spell");
    public static final ResourceKey<Registry<Spell>> KEY = ResourceKey.createRegistryKey(ID);
    public static Registry<Spell> from(Level world) {
        return world.registryAccess().lookupOrThrow(KEY);
    }
    
    private static final Gson gson = new GsonBuilder().create();
    /**
     * Resolved eagerly on the class-init thread. `Spell` is a recursive type, and Gson builds such adapters lazily
     * through a `FutureTypeAdapter`; when several registry-loading worker threads (registry elements load in
     * parallel since 26.1) call `gson.fromJson(json, Spell.class)` for the first time concurrently, one of them
     * fails with "Adapter for type with cyclic dependency has been used before dependency has been resolved".
     */
    private static final TypeAdapter<Spell> SPELL_ADAPTER = gson.getAdapter(Spell.class);

    /// Spell JSON as-is, handed to GSON. Only lossless over JSON-shaped ops:
    /// NBT has no boolean type and no mixed-type lists, so a JSON→NBT→JSON round trip breaks spells.
    private static final Codec<Spell> JSON_CODEC = ExtraCodecs.JSON.xmap(
            SPELL_ADAPTER::fromJsonTree,
            SPELL_ADAPTER::toJsonTree
    );

    /// Spell JSON as opaque bytes, wrapped in a map so the serialized root is a compound.
    /// (Packet inspecting tools, such as Replay Mod, assume compound roots in the registry sync packet.)
    private static final Codec<Spell> BYTES_CODEC = Codec.BYTE_BUFFER.comapFlatMap(
            encoded -> {
                var json = new String(encoded.array(), StandardCharsets.UTF_8);
                return DataResult.success(SPELL_ADAPTER.fromJsonTree(JsonParser.parseString(json)));
            },
            spell -> ByteBuffer.wrap(SPELL_ADAPTER.toJson(spell).getBytes(StandardCharsets.UTF_8))
    ).fieldOf("data").codec();

    /// Single codec for data pack loading and network sync.
    /// Picks the shape by the ops it is handed: plain spell JSON for JSON ops (data pack files, datagen,
    /// and the client parsing its local files for entries the server omits as "known pack" data),
    /// opaque bytes for anything else (NBT in the registry sync packet).
    public static final Codec<Spell> CODEC = new Codec<>() {
        @Override
        public <T> DataResult<Pair<Spell, T>> decode(DynamicOps<T> ops, T input) {
            return delegate(ops).decode(ops, input);
        }

        @Override
        public <T> DataResult<T> encode(Spell input, DynamicOps<T> ops, T prefix) {
            return delegate(ops).encode(input, ops, prefix);
        }

        private static Codec<Spell> delegate(DynamicOps<?> ops) {
            // RegistryOps delegates `empty()`, so this sees through the wrapper
            return ops.empty() instanceof JsonElement ? JSON_CODEC : BYTES_CODEC;
        }

        @Override
        public String toString() {
            return "SpellRegistry.CODEC";
        }
    };

    public static HolderSet.Named<Spell> find(Level world, Identifier tagId) {
        var manager = world.registryAccess();
        var lookup = manager.lookupOrThrow(KEY);
        var tag = TagKey.create(KEY, tagId);
        return lookup.getOrThrow(tag);
    }

    public static List<Holder<Spell>> entries(Level world, @Nullable Identifier id) {
        try {
            return find(world, id).stream().toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    public static List<Holder<Spell>> entries(Level world, @Nullable String pool) {
        if (pool == null || pool.isEmpty()) {
            return List.of();
        }
        var id = Identifier.parse(pool);
        return entries(world, id);
    }

    public static Stream<Holder.Reference<Spell>> stream(Level world) {
        var manager = world.registryAccess();
        var registry = manager.lookupOrThrow(KEY);
        return registry.listElements();
    }
}