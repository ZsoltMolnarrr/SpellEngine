package net.spell_engine.api.datagen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.minecraft.data.DataOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.DataWriter;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public abstract class SimpleSoundGeneratorV2 implements DataProvider {
    private final CompletableFuture<RegistryWrapper.WrapperLookup> registryLookup;
    protected final FabricDataOutput dataOutput;

    public SimpleSoundGeneratorV2(FabricDataOutput dataOutput, CompletableFuture<RegistryWrapper.WrapperLookup> registryLookup) {
        this.dataOutput = dataOutput;
        this.registryLookup = registryLookup;
    }

    public record Entry(String namespace, List<SoundEntry> sounds) {  }
    public record SoundEntry(String name, List<String> variants) {
        public static SoundEntry withVariants(String name, int count) {
            if (count > 1) {
                List<String> variants = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    variants.add(name + "_" + (i+1));
                }
                return new SoundEntry(name, variants);
            } else {
                return new SoundEntry(name, List.of(name));
            }
        }
    }
    public static class Builder {
        public final List<Entry> entries = new ArrayList<>();
    }

    public abstract void generateSounds(Builder builder);

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private record Sound(List<String> sounds) { }
    private static LinkedHashMap<String, Sound> createFileContent(String namespace, List<SoundEntry> soundEntries) {
        var map = new LinkedHashMap<String, Sound>();
        for (var sound: soundEntries) {
            var soundIds = sound.variants().stream().map(variant -> Identifier.of(namespace, variant).toString()).toList();
            map.put(sound.name, new Sound(soundIds));
        }
        return map;
    }

    @Override
    public CompletableFuture<?> run(DataWriter writer) {
        var builder = new Builder();
        generateSounds(builder);
        var entries = builder.entries;

        List<CompletableFuture> writes = new ArrayList<>();
        for (var entry: entries) {
            var content = createFileContent(entry.namespace(), entry.sounds);
            var json = gson.toJsonTree(content);
            var path = getFilePath(entry.namespace());
            writes.add(DataProvider.writeToPath(writer, json, path));
        }

        return CompletableFuture.allOf(writes.toArray(new CompletableFuture[0]));
    }

    @Override
    public String getName() {
        return "Simple Sound Entry Generator";
    }

    private Path getFilePath(String namespace) {
        return this.dataOutput.getResolver(DataOutput.OutputType.RESOURCE_PACK, "").resolveJson(Identifier.of(namespace, "sounds"));
    }
}
