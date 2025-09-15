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
import java.util.List;
import java.util.concurrent.CompletableFuture;

public abstract class SimpleParticleGenerator implements DataProvider {
    private final CompletableFuture<RegistryWrapper.WrapperLookup> registryLookup;
    protected final FabricDataOutput dataOutput;

    public SimpleParticleGenerator(FabricDataOutput dataOutput, CompletableFuture<RegistryWrapper.WrapperLookup> registryLookup) {
        this.dataOutput = dataOutput;
        this.registryLookup = registryLookup;
    }

    public record ParticleData(List<String> textures) { }
    public record Entry(Identifier id, ParticleData particle) { }
    public static class Builder {
        private final List<Entry> entries = new ArrayList<>();
        public void add(Identifier id, ParticleData spell) {
            entries.add(new SimpleParticleGenerator.Entry(id, spell));
        }
    }

    public abstract void generateSimpleParticles(SimpleParticleGenerator.Builder builder);

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public CompletableFuture<?> run(DataWriter writer) {
        var builder = new SimpleParticleGenerator.Builder();
        generateSimpleParticles(builder);
        var entries = builder.entries;

        List<CompletableFuture> writes = new ArrayList<>();
        for (var entry: entries) {
            var data = entry.particle();
            var id = entry.id;
            var json = gson.toJsonTree(data);
            var path = getFilePath(id);
            writes.add(DataProvider.writeToPath(writer, json, path));
        }

        return CompletableFuture.allOf(writes.toArray(new CompletableFuture[0]));
    }

    @Override
    public String getName() {
        return "Simple Particle Generator";
    }

    private Path getFilePath(Identifier spellId) {
        return this.dataOutput.getResolver(DataOutput.OutputType.RESOURCE_PACK, "particles").resolveJson(spellId);
    }
}
