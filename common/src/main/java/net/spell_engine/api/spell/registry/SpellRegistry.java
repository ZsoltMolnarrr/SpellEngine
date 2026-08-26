package net.spell_engine.api.spell.registry;

import com.google.gson.*;
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
    public static final Identifier ID = Identifier.withDefaultNamespace("spell");
    public static final ResourceKey<Registry<Spell>> KEY = ResourceKey.createRegistryKey(ID);
    public static Registry<Spell> from(Level world) {
        return world.registryAccess().lookupOrThrow(KEY);
    }
    
    private static final Gson gson = new GsonBuilder().create();
    public static final Codec<Spell> LOCAL_CODEC = ExtraCodecs.JSON.xmap(
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