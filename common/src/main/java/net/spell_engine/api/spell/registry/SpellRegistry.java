package net.spell_engine.api.spell.registry;

import com.google.gson.*;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.dynamic.Codecs;
import net.minecraft.world.World;
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
    public static final Identifier ID = Identifier.ofVanilla("spell");
    public static final RegistryKey<Registry<Spell>> KEY = RegistryKey.ofRegistry(ID);
    public static Registry<Spell> from(World world) {
        return world.getRegistryManager().get(KEY);
    }
    
    private static final Gson gson = new GsonBuilder().create();

    /// Spell JSON as-is, handed to GSON. Only lossless over JSON-shaped ops:
    /// NBT has no boolean type and no mixed-type lists, so a JSON→NBT→JSON round trip breaks spells.
    private static final Codec<Spell> JSON_CODEC = Codecs.JSON_ELEMENT.xmap(
            json -> gson.fromJson(json, Spell.class),
            spell -> gson.toJsonTree(spell)
    );

    /// Spell JSON as opaque bytes, wrapped in a map so the serialized root is a compound.
    /// (Packet inspecting tools, such as Replay Mod, assume compound roots in the registry sync packet.)
    private static final Codec<Spell> BYTES_CODEC = Codec.BYTE_BUFFER.comapFlatMap(
            encoded -> {
                var json = new String(encoded.array(), StandardCharsets.UTF_8);
                return DataResult.success(gson.fromJson(json, Spell.class));
            },
            spell -> ByteBuffer.wrap(gson.toJson(spell).getBytes(StandardCharsets.UTF_8))
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

    public static RegistryEntryList.Named<Spell> find(World world, Identifier tagId) {
        var manager = world.getRegistryManager();
        var lookup = manager.createRegistryLookup().getOrThrow(KEY); // RegistryEntryLookup<Spell>
        var tag = TagKey.of(KEY, tagId);
        return lookup.getOrThrow(tag);
    }

    public static List<RegistryEntry<Spell>> entries(World world, @Nullable Identifier id) {
        try {
            return find(world, id).stream().toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    public static List<RegistryEntry<Spell>> entries(World world, @Nullable String pool) {
        if (pool == null || pool.isEmpty()) {
            return List.of();
        }
        var id = Identifier.of(pool);
        return entries(world, id);
    }

    public static Stream<RegistryEntry.Reference<Spell>> stream(World world) {
        var manager = world.getRegistryManager();
        var registry = manager.get(KEY);
        return registry.streamEntries();
    }
}