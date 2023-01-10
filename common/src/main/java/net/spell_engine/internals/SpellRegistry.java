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

import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

public class SpellRegistry {
    private static final Map<Identifier, Spell> spells = new HashMap();
    private static final Map<Identifier, SpellContainer> containers = new HashMap();

    public static Map<Identifier, Spell> all() {
        return spells;
    }

    public static void initialize() {
        ServerLifecycleEvents.SERVER_STARTED.register((minecraftServer) -> {
            loadSpells(minecraftServer.getResourceManager());
            loadContainers(minecraftServer.getResourceManager());
            encodeContent();
        });
    }

    public static void loadSpells(ResourceManager resourceManager) {
        var gson = new Gson();
        Map<Identifier, Spell> parsed = new HashMap();
        // Reading all attribute files
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
                parsed.put(new Identifier(id), container);
                // System.out.println("loaded spell - id: " + id +  " spell: " + gson.toJson(container));
            } catch (Exception e) {
                System.err.println("Failed to parse spell: " + identifier);
                e.printStackTrace();
            }
        }
        spells.clear();
        spells.putAll(parsed);
        updateRawIds();
    }

    public static void loadContainers(ResourceManager resourceManager) {
        var gson = new Gson();
        Map<Identifier, SpellContainer> parsed = new HashMap();
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
        containers.putAll(parsed);
    }

    public static Spell spell(SpellContainer container, int selectedIndex) {
        var string = container.spellId(selectedIndex);
        if (string == null) {
            return null;
        }
        var spellId = new Identifier(string);
        return getSpell(spellId);
    }

    public static Identifier spellId(SpellContainer container, int selectedIndex) {
        return new Identifier(container.spellId(selectedIndex));
    }

    public static SpellContainer containerForItem(Identifier itemId) {
        if (itemId == null) {
            return null;
        }
        return containers.get(itemId);
    }

    public static Spell getSpell(Identifier spellId) {
        return spells.get(spellId);
    }

    public static PacketByteBuf encoded = PacketByteBufs.create();

    public static class SyncFormat { public SyncFormat() { }
        private Map<String, Spell> spells = new HashMap();
        private Map<String, SpellContainer> containers = new HashMap();
    }

    private static void encodeContent() {
        var gson = new Gson();
        var buffer = PacketByteBufs.create();

        var sync = new SyncFormat();
        spells.forEach((key, value) -> {
            sync.spells.put(key.toString(), value);
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
        sync.containers.forEach((key, value) -> {
            containers.put(new Identifier(key), value);
        });
        updateRawIds();
    }

    private static final Map<Identifier, Integer> rawMap = new HashMap<>();

    private static void updateRawIds() {
        rawMap.clear();
        var sortedIDs = spells.keySet()
                .stream()
                .sorted(Comparator.comparing(Identifier::toString))
                .toList();
        int rawId = 1;
        for(var id: sortedIDs) {
            rawMap.put(id, rawId);
            rawId += 1;
        }
    }

    public static int rawId(Identifier identifier) {
        return rawMap.get(identifier);
    }

    public static Optional<Identifier> fromRawId(int rawId) {
        for(var entry: rawMap.entrySet()) {
            if(entry.getValue() == rawId) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }
}
