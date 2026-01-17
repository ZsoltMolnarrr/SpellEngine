package net.spell_engine.api.spell.registry;

import com.google.gson.*;
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
import java.util.Base64;
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
    public static final Codec<Spell> LOCAL_CODEC = Codecs.JSON_ELEMENT.xmap(
            json -> {
                return gson.fromJson(json, Spell.class);
            },
            spell -> {
                JsonElement jsonElement = gson.toJsonTree(spell);
                return jsonElement;
            }
    );

    public static final Codec<Spell> NETWORK_CODEC_V2 = Codec.BYTE_BUFFER.comapFlatMap(
            encoded -> {
                var bytes = encoded.array();
                var json = new String(bytes);
                var spell = gson.fromJson(json, Spell.class);
                return DataResult.success(spell);
            },
            spell -> {
                var json = gson.toJson(spell);
                var bytes = json.getBytes();
                return ByteBuffer.wrap(bytes);
            }
    );

    public static final Codec<Spell> NETWORK_CODEC = Codec.STRING.comapFlatMap(
            encoded -> {
                var bytes = encoded.getBytes();
                var json = new String(Base64.getDecoder().decode(bytes));
                var spell = gson.fromJson(json, Spell.class);
                return DataResult.success(spell);
            },
            spell -> {
                var json = gson.toJson(spell);
                var bytes = json.getBytes();
                return Base64.getEncoder().encodeToString(bytes);
            }
    );

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