package net.spell_engine.api.datagen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public abstract class SimpleSoundGenerator implements DataProvider {
    private final CompletableFuture<HolderLookup.Provider> registryLookup;
    protected final FabricPackOutput dataOutput;

    public SimpleSoundGenerator(FabricPackOutput dataOutput, CompletableFuture<HolderLookup.Provider> registryLookup) {
        this.dataOutput = dataOutput;
        this.registryLookup = registryLookup;
    }

    public record Entry(String namespace, List<String> sounds) {  }
    public static class Builder {
        public final List<Entry> entries = new ArrayList<>();
    }

    public abstract void generateSounds(Builder builder);

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private record Sound(List<String> sounds) { }
    private static LinkedHashMap<String, Sound> createFileContent(String namespace, List<String> sounds) {
        var map = new LinkedHashMap<String, Sound>();
        for (var sound: sounds) {
            map.put(sound, new Sound(List.of(Identifier.fromNamespaceAndPath(namespace, sound).toString())));
        }
        return map;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput writer) {
        var builder = new Builder();
        generateSounds(builder);
        var entries = builder.entries;

        List<CompletableFuture> writes = new ArrayList<>();
        for (var entry: entries) {
            var content = createFileContent(entry.namespace(), entry.sounds);
            var json = gson.toJsonTree(content);
            var path = getFilePath(entry.namespace());
            writes.add(DataProvider.saveStable(writer, json, path));
        }

        return CompletableFuture.allOf(writes.toArray(new CompletableFuture[0]));
    }

    @Override
    public String getName() {
        return "Simple Sound Entry Generator";
    }

    private Path getFilePath(String namespace) {
        // 26.1.2: PathProvider.file() builds "<kind>/<path>", so an empty kind would yield the absolute "/sounds.json" — resolve directly.
        return this.dataOutput.getOutputFolder(PackOutput.Target.RESOURCE_PACK).resolve(namespace).resolve("sounds.json");
    }
}
