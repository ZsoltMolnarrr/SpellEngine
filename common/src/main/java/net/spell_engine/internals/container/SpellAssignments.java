package net.spell_engine.internals.container;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resource.ResourceManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.spell_engine.api.spell.container.SpellChoice;
import net.spell_engine.api.spell.container.SpellContainer;
import net.spell_engine.utils.WeaponCompatibility;
import org.jetbrains.annotations.Nullable;

import java.io.InputStreamReader;
import java.util.*;

public class SpellAssignments {
    // Could be turned into a separate registry
    // BUT! Vanilla registries cannot be inserted programatically
    // (So SpellBook container assignment, and fallback/auto assignments would not be possible)
    // Resolution:
    // - SpellBook containers need no assignment, applying item component is suitable, or datafile can be added by devs
    // - Fallback/auto assignments ??? - MAYBE Inject(TAIL) RegistryLoader.loadFromResource (probably wont be synced to clients)

    /// One `spell_assignments/<item_path>.json` entry. Both members are optional, so a pack can retune only
    /// the container, only the choice, or both.
    ///
    /// ```json
    /// { "spell_container": { ... }, "spell_choice": { ... } }
    /// ```
    public record Assignment(@Nullable SpellContainer container, @Nullable SpellChoice choice) {
        public static final String CONTAINER_KEY = "spell_container";
        public static final String CHOICE_KEY = "spell_choice";

        public static final Codec<Assignment> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                SpellContainer.CODEC.optionalFieldOf(CONTAINER_KEY).forGetter(x -> Optional.ofNullable(x.container)),
                SpellChoice.CODEC.optionalFieldOf(CHOICE_KEY).forGetter(x -> Optional.ofNullable(x.choice))
        ).apply(instance, (container, choice) -> new Assignment(container.orElse(null), choice.orElse(null))));

        public boolean isEmpty() {
            return container == null && choice == null;
        }
    }

    /// Assignments read from datapacks (`data/<namespace>/spell_assignments/<item_path>.json`).
    ///
    /// These **outrank the item's own default** container/choice, which is what lets a pack retune a weapon
    /// shipped by a content mod. Deliberately kept apart from [#containers]: that map also collects the
    /// weapon fallback config, which must stay a last resort (RPG Series weapons extend the very vanilla item
    /// classes the fallback compat groups match on, so a promoted fallback would clobber first-party defaults).
    public static final Map<Identifier, Assignment> assignments = new HashMap<>();

    /// Programmatic (spell book) and fallback-config containers. Last resort: consulted only when neither the
    /// stack, nor a datapack assignment, nor the item's own default provides a container.
    public static final Map<Identifier, SpellContainer> containers = new HashMap<>();
    public static final Map<Identifier, SpellContainer> book_containers = new HashMap<>();

    public static void init() {
        net.spell_engine.PlatformEvents.onServerStarting(SpellAssignments::load);
    }

    private static void load(MinecraftServer minecraftServer) {
        loadContainers(minecraftServer.getResourceManager());
        WeaponCompatibility.initialize();
        encodeContent();
    }

    public static void loadContainers(ResourceManager resourceManager) {
        Map<Identifier, Assignment> parsed = new HashMap<>();
        var directory = "spell_assignments";
        for (var entry : resourceManager.findResources(directory, fileName -> fileName.getPath().endsWith(".json")).entrySet()) {
            var identifier = entry.getKey();
            var resource = entry.getValue();
            try {
                Identifier itemId;
                var id = identifier.toString().replace(directory + "/", "");
                itemId = new Identifier(id.substring(0, id.lastIndexOf('.')));

                JsonElement root;
                try (var reader = new InputStreamReader(resource.getInputStream())) {
                    root = JsonParser.parseReader(reader);
                }
                var assignment = parseAssignment(identifier, root);
                if (assignment != null) {
                    parsed.put(itemId, assignment);
                }
            } catch (Exception e) {
                warn(identifier, e.getMessage());
            }
        }
        assignments.clear();
        assignments.putAll(parsed);

        containers.clear();
        containers.putAll(book_containers);
    }

    /// Reads one assignment file, best effort: `null` (plus a console warning) whenever the file carries
    /// nothing usable, never an exception, so one bad file cannot abort the rest of the load.
    ///
    /// The wrapper is required, and its presence is tested by **explicit key presence**: every field of both
    /// `SpellContainer.CODEC` and `SpellChoice.CODEC` is optional, so an unwrapped file would decode
    /// *successfully* as a bare container - into an all-defaults one. Since assignments outrank item defaults,
    /// accepting that would silently strip the named item of its spells, so it is rejected loudly instead.
    @Nullable
    private static Assignment parseAssignment(Identifier fileId, @Nullable JsonElement root) {
        if (root == null || root.isJsonNull()) {
            warn(fileId, "file is empty");
            return null;
        }
        if (!root.isJsonObject()) {
            warn(fileId, "expected a JSON object at the top level");
            return null;
        }
        JsonObject object = root.getAsJsonObject();

        if (!object.has(Assignment.CONTAINER_KEY) && !object.has(Assignment.CHOICE_KEY)) {
            warn(fileId, "missing both '" + Assignment.CONTAINER_KEY + "' and '" + Assignment.CHOICE_KEY
                    + "' - the wrapper format is required: { \"" + Assignment.CONTAINER_KEY + "\": { ... }, \""
                    + Assignment.CHOICE_KEY + "\": { ... } } (both members optional). A file holding a bare"
                    + " SpellContainer object is no longer supported: wrap its contents in '"
                    + Assignment.CONTAINER_KEY + "'.");
            return null;
        }

        // Note the all-defaults container `{ "spell_container": { } }` decodes to an invalid (unusable)
        // container on purpose: that is the documented way to strip an item of spell casting, and now - since
        // assignments outrank item defaults - it strips first-party weapons too. So container validity is
        // deliberately not checked.
        var container = member(fileId, object, Assignment.CONTAINER_KEY, SpellContainer.CODEC);
        var choice = member(fileId, object, Assignment.CHOICE_KEY, SpellChoice.CODEC);
        var assignment = new Assignment(container, choice);
        if (assignment.isEmpty()) {
            warn(fileId, "neither '" + Assignment.CONTAINER_KEY + "' nor '" + Assignment.CHOICE_KEY + "' resolved to a value");
            return null;
        }
        return assignment;
    }

    @Nullable
    private static <T> T member(Identifier fileId, JsonObject object, String key, Codec<T> codec) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        return decode(fileId, key, object.get(key), codec);
    }

    /// Decodes with the same codec the item data uses, so an assignment file and an item's own
    /// container/choice are spelled identically. Warns and yields `null` on any failure.
    @Nullable
    private static <T> T decode(Identifier fileId, String what, JsonElement element, Codec<T> codec) {
        try {
            var result = codec.parse(JsonOps.INSTANCE, element);
            var error = result.error();
            if (error.isPresent()) {
                warn(fileId, "'" + what + "' could not be read: " + error.get().message());
                return null;
            }
            return result.result().orElse(null);
        } catch (Exception e) {
            // Some codecs (`SpellContainer.ContentType`) throw rather than returning an error result
            warn(fileId, "'" + what + "' could not be read: " + e);
            return null;
        }
    }

    private static void warn(Identifier fileId, String reason) {
        System.err.println("Spell Engine: Skipping spell_assignment: " + fileId + " | Reason: " + reason);
    }

    // MARK: Lookups

    /// Datapack assignment for an item, `null` when the item has none.
    /// Outranks the item's own default container/choice.
    @Nullable
    public static Assignment assignment(@Nullable Identifier itemId) {
        return itemId != null ? assignments.get(itemId) : null;
    }

    /// Container an item gets from data alone - the datapack assignment, else the programmatic/fallback map.
    /// The item's own default is *not* considered here; the full chain lives in
    /// `SpellContainerHelper#containerFromItemStack`.
    @Nullable
    public static SpellContainer containerForItem(@Nullable Identifier itemId) {
        var assignment = assignment(itemId);
        if (assignment != null && assignment.container() != null) {
            return assignment.container();
        }
        return fallbackContainerForItem(itemId);
    }

    /// Last-resort container: spell book assignments and the weapon fallback config. Below item defaults.
    @Nullable
    public static SpellContainer fallbackContainerForItem(@Nullable Identifier itemId) {
        return itemId != null ? containers.get(itemId) : null;
    }

    // MARK: Sync

    public static List<String> encoded = List.of();

    private static final Codec<Map<Identifier, Assignment>> ASSIGNMENTS_CODEC =
            Codec.unboundedMap(Identifier.CODEC, Assignment.CODEC);
    private static final Codec<Map<Identifier, SpellContainer>> CONTAINERS_CODEC =
            Codec.unboundedMap(Identifier.CODEC, SpellContainer.CODEC);

    private static final String SYNC_ASSIGNMENTS = "assignments";
    private static final String SYNC_CONTAINERS = "containers";

    private static void encodeContent() {
        var sync = new JsonObject();
        sync.add(SYNC_ASSIGNMENTS, ASSIGNMENTS_CODEC.encodeStart(JsonOps.INSTANCE, assignments)
                .resultOrPartial(error -> System.err.println("Spell Engine: Failed to encode spell assignments: " + error))
                .orElseGet(JsonObject::new));
        sync.add(SYNC_CONTAINERS, CONTAINERS_CODEC.encodeStart(JsonOps.INSTANCE, containers)
                .resultOrPartial(error -> System.err.println("Spell Engine: Failed to encode spell containers: " + error))
                .orElseGet(JsonObject::new));
        var json = sync.toString();

        List<String> chunks = new ArrayList<>();
        var chunkSize = 10000;
        for (int i = 0; i < json.length(); i += chunkSize) {
            chunks.add(json.substring(i, Math.min(json.length(), i + chunkSize)));
        }

        System.out.println("Encoded SpellAssignments size: " + json.length()
                + " chars (in " + chunks.size() + " string chunks with the size of "  + chunkSize + ")");

        encoded = chunks;
    }

    public static void decodeContent(List<String> chunks) {
        var json = String.join("", chunks);
        try {
            var root = JsonParser.parseString(json).getAsJsonObject();
            assignments.clear();
            assignments.putAll(decodeSynced(root, SYNC_ASSIGNMENTS, ASSIGNMENTS_CODEC));
            containers.clear();
            containers.putAll(decodeSynced(root, SYNC_CONTAINERS, CONTAINERS_CODEC));
        } catch (Exception e) {
            System.err.println("Spell Engine: Failed to decode synced spell assignments | Reason: " + e);
        }
    }

    private static <T> Map<Identifier, T> decodeSynced(JsonObject root, String key, Codec<Map<Identifier, T>> codec) {
        var element = root.get(key);
        if (element == null || element.isJsonNull()) {
            return Map.of();
        }
        Optional<Map<Identifier, T>> decoded = codec.parse(JsonOps.INSTANCE, element)
                .resultOrPartial(error -> System.err.println("Spell Engine: Failed to decode synced " + key + ": " + error));
        return decoded.orElse(Map.of());
    }
}
