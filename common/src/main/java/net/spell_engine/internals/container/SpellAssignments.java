package net.spell_engine.internals.container;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resource.ResourceManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.spell_engine.api.spell.container.SpellContainer;
import net.spell_engine.utils.WeaponCompatibility;

import java.io.InputStreamReader;
import java.util.*;

public class SpellAssignments {
    // Simply move it into SpellBooks.java

    // Could be turned into a separate registry
    // BUT! Vanilla registries cannot be inserted programatically
    // (So SpellBook container assignment, and fallback/auto assignments would not be possible)
    // Resolution:
    // - SpellBook containers need no assignment, applying item component is suitable, or datafile can be added by devs
    // - Fallback/auto assignments ??? - MAYBE Inject(TAIL) RegistryLoader.loadFromResource (probably wont be synced to clients)
    public static final Map<Identifier, SpellContainer> containers = new HashMap<>();
    public static final Map<Identifier, SpellContainer> book_containers = new HashMap<>();

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTING.register(SpellAssignments::load);
    }

    private static void load(MinecraftServer minecraftServer) {
        loadContainers(minecraftServer.getResourceManager());
        WeaponCompatibility.initialize();
        encodeContent();
    }

    public static void loadContainers(ResourceManager resourceManager) {
        var gson = new Gson();
        Map<Identifier, SpellContainer> parsed = new HashMap<>();
        // Reading all attribute files
        var directory = "spell_assignments";
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
                parsed.put(Identifier.of(id), container);
                // System.out.println("loaded assignment - id: " + id +  " assignment: " + contaisner);
            } catch (Exception e) {
                System.err.println("Spell Engine: Failed to parse spell_assignment: " + identifier + " | Reason: " + e.getMessage());
            }
        }
        containers.clear();
        containers.putAll(parsed);
        containers.putAll(book_containers);
    }

    public static SpellContainer containerForItem(Identifier itemId) {
        if (itemId == null) {
            return null;
        }
        return containers.get(itemId);
    }

    public static List<String> encoded = List.of();

    public static class SyncFormat { public SyncFormat() { }
        public Map<String, SpellContainer> containers = new HashMap<>();
    }

    private static void encodeContent() {
        var gson = new Gson();

        var sync = new SyncFormat();
        containers.forEach((key, value) -> {
            sync.containers.put(key.toString(), value);
        });
        var json = gson.toJson(sync);

        List<String> chunks = new ArrayList<>();
        var chunkSize = 10000;
        for (int i = 0; i < json.length(); i += chunkSize) {
            chunks.add(json.substring(i, Math.min(json.length(), i + chunkSize)));
        }

        System.out.println("Encoded SpellRegistry size (with package overhead): " + "???"
                + " bytes (in " + chunks.size() + " string chunks with the size of "  + chunkSize + ")");

        encoded = chunks;
    }

    public static void decodeContent(List<String> chunks) {
        String json = "";
        for (var chunk: chunks) {
            json = json.concat(chunk);
        }
        var gson = new Gson();
        SyncFormat sync = gson.fromJson(json, SyncFormat.class);
        sync.containers.forEach((key, value) -> {
            containers.put(Identifier.of(key), value);
        });
    }
}
