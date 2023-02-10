package net.spell_engine.internals;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.SpellContainer;
import net.spell_engine.api.spell.SpellPool;
import net.spell_power.api.MagicSchool;
import org.jetbrains.annotations.Nullable;

import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

public class SpellRegistry {
    public static class Entry { public Entry() { }
        public Spell spell;
        public int rawId;
        public Entry(Spell spell, int rawId) {
            this.spell = spell;
            this.rawId = rawId;
        }
    }
    private static final Map<Identifier, Entry> spells = new HashMap<>();
    private static final Map<Identifier, SpellPool> pools = new HashMap<>();
    private static final Map<Identifier, SpellContainer> containers = new HashMap<>();
    private static final Map<MagicSchool, Integer> spellCount = new HashMap<>();

    public static Map<Identifier, Entry> all() {
        return spells;
    }

    public static void initialize() {
        ServerLifecycleEvents.SERVER_STARTED.register((minecraftServer) -> {
            loadSpells(minecraftServer.getResourceManager());
            loadPools(minecraftServer.getResourceManager());
            loadContainers(minecraftServer.getResourceManager());
            encodeContent();
        });
    }

    public static void loadSpells(ResourceManager resourceManager) {
        var gson = new Gson();
        Map<Identifier, Entry> parsed = new HashMap<>();
        // Reading all attribute files
        int rawId = 1;
        var directory = "spells";
        for (var entry : resourceManager.findResources(directory, fileName -> fileName.getPath().endsWith(".json")).entrySet()) {
            var identifier = entry.getKey();
            var resource = entry.getValue();
            try {
                // System.out.println("Checking resource: " + identifier);
                JsonReader reader = new JsonReader(new InputStreamReader(resource.getInputStream()));
                Spell container = gson.fromJson(reader, Spell.class);
                var id = identifier
                        .toString().replace(directory + "/", "");
                id = id.substring(0, id.lastIndexOf('.'));
                Validator.validate(container);
                parsed.put(new Identifier(id), new Entry(container, rawId++));
                // System.out.println("loaded spell - id: " + id +  " spell: " + gson.toJson(container));
            } catch (Exception e) {
                System.err.println("Failed to parse spell: " + identifier);
                e.printStackTrace();
            }
        }
        spells.clear();
        spells.putAll(parsed);
        spellsUpdated();
    }

    public static void loadPools(ResourceManager resourceManager) {
        var gson = new Gson();
        Map<Identifier, SpellPool.DataFormat> parsed = new HashMap<>();
        // Reading all attribute files
        var directory = "spell_pools";
        for (var entry : resourceManager.findResources(directory, fileName -> fileName.getPath().endsWith(".json")).entrySet()) {
            var identifier = entry.getKey();
            var resource = entry.getValue();
            try {
                // System.out.println("Checking resource: " + identifier);
                JsonReader reader = new JsonReader(new InputStreamReader(resource.getInputStream()));
                SpellPool.DataFormat pool = gson.fromJson(reader, SpellPool.DataFormat.class);
                var id = identifier
                        .toString().replace(directory + "/", "");
                id = id.substring(0, id.lastIndexOf('.'));
                parsed.put(new Identifier(id), pool);
                System.out.println("loaded pool - " + id +  " ids: " + pool.spell_ids);
            } catch (Exception e) {
                System.err.println("Failed to parse spell_pool: " + identifier);
                e.printStackTrace();
            }
        }
        Map<Identifier, Spell> spellFlat = spells.entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().spell));
        pools.clear();
        for (var entry: parsed.entrySet()) {
            pools.put(entry.getKey(), SpellPool.fromData(entry.getValue(), spellFlat));
        }
    }

    public static void loadContainers(ResourceManager resourceManager) {
        var gson = new Gson();
        Map<Identifier, SpellContainer> parsed = new HashMap<>();
        // Reading all attribute files
        var directory = "item_spell_assignment";
        for (var entry : resourceManager.findResources(directory, fileName -> fileName.getPath().endsWith(".json")).entrySet()) {
            var identifier = entry.getKey();
            var resource = entry.getValue();
            try {
                // System.out.println("Checking resource: " + identifier);
                JsonReader reader = new JsonReader(new InputStreamReader(resource.getInputStream()));
                SpellContainer container = gson.fromJson(reader, SpellContainer.class);
                var id = identifier
                        .toString().replace(directory + "/", "");
                id = id.substring(0, id.lastIndexOf('.'));
                parsed.put(new Identifier(id), container);
                // System.out.println("loaded assignment - id: " + id +  " assignment: " + container.spell);
            } catch (Exception e) {
                System.err.println("Failed to parse item_spell_assignment: " + identifier);
                e.printStackTrace();
            }
        }
        containers.clear();
        containers.putAll(parsed);
    }

    private static void spellsUpdated() {
        updateReverseMap();
        spellCount.clear();
        for(var school: MagicSchool.values()) {
            spellCount.put(school, 0);
        }
        for(var spell: spells.entrySet()) {
            var school = spell.getValue().spell.school;
            var current = spellCount.get(school);
            spellCount.put(school, current + 1);
        }
    }

    public static int numberOfSpells(MagicSchool school) {
        return spellCount.get(school);
    }

    public static SpellContainer containerForItem(Identifier itemId) {
        if (itemId == null) {
            return null;
        }
        return containers.get(itemId);
    }

    public static Spell getSpell(Identifier spellId) {
        var entry = spells.get(spellId);
        if (entry != null) {
            return entry.spell;
        }
        return null;
    }

    @Nullable
    public static SpellPool spellPool(Identifier id) {
        return pools.get(id);
    }

    public static PacketByteBuf encoded = PacketByteBufs.create();

    public static class SyncFormat { public SyncFormat() { }
        public Map<String, Entry> spells = new HashMap<>();
        public Map<String, SpellPool.SyncFormat> pools = new HashMap<>();
        public Map<String, SpellContainer> containers = new HashMap<>();
    }

    private static void encodeContent() {
        var gson = new Gson();
        var buffer = PacketByteBufs.create();

        var sync = new SyncFormat();
        spells.forEach((key, value) -> {
            sync.spells.put(key.toString(), value);
        });
        pools.forEach((key, value) -> {
            sync.pools.put(key.toString(), value.toSync());
        });
        containers.forEach((key, value) -> {
            sync.containers.put(key.toString(), value);
        });
        var json = gson.toJson(sync);

        List<String> chunks = new ArrayList<>();
        var chunkSize = 10000;
        for (int i = 0; i < json.length(); i += chunkSize) {
            chunks.add(json.substring(i, Math.min(json.length(), i + chunkSize)));
        }
        buffer.writeInt(chunks.size());
        for (var chunk: chunks) {
            buffer.writeString(chunk);
        }

        System.out.println("Encoded SpellRegistry size (with package overhead): " + buffer.readableBytes()
                + " bytes (in " + chunks.size() + " string chunks with the size of "  + chunkSize + ")");

        encoded = buffer;
    }

    public static void decodeContent(PacketByteBuf buffer) {
        var chunkCount = buffer.readInt();
        String json = "";
        for (int i = 0; i < chunkCount; ++i) {
            json = json.concat(buffer.readString());
        }
        var gson = new Gson();
        SyncFormat sync = gson.fromJson(json, SyncFormat.class);
        spells.clear();
        sync.spells.forEach((key, value) -> {
            spells.put(new Identifier(key), value);
        });
        sync.pools.forEach((key, value) -> {
            pools.put(new Identifier(key), SpellPool.fromSync(value));
        });
        sync.containers.forEach((key, value) -> {
            containers.put(new Identifier(key), value);
        });
        spellsUpdated();
    }

    private record ReverseEntry(Identifier identifier, Spell spell) { }
    private static final Map<Integer, ReverseEntry> reverseMap = new HashMap<>();

    private static void updateReverseMap() {
        reverseMap.clear();
        for (var entry: spells.entrySet()) {
            var id = entry.getKey();
            var spell = entry.getValue().spell;
            var rawId = entry.getValue().rawId;
            reverseMap.put(rawId, new ReverseEntry(id, spell));
        }
    }

    public static int rawId(Identifier identifier) {
        return spells.get(identifier).rawId;
    }

    public static Optional<Identifier> fromRawId(int rawId) {
        var reverseEntry = reverseMap.get(rawId);
        if (reverseEntry != null) {
            return Optional.of(reverseEntry.identifier);
        }
        return Optional.empty();
    }
}
