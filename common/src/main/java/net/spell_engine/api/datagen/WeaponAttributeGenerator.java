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

public abstract class WeaponAttributeGenerator implements DataProvider {
    private final CompletableFuture<RegistryWrapper.WrapperLookup> registryLookup;
    protected final FabricDataOutput dataOutput;

    public WeaponAttributeGenerator(FabricDataOutput dataOutput, CompletableFuture<RegistryWrapper.WrapperLookup> registryLookup) {
        this.dataOutput = dataOutput;
        this.registryLookup = registryLookup;
    }

    public record Entry(Identifier id, String preset) {  }
    public static class Builder {
        public final List<Entry> entries = new ArrayList<>();
    }

    public abstract void generateWeaponAttributes(Builder builder);

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private record WeaponAttributesFile(String parent) { }
    private static WeaponAttributesFile createFileContent(String preset) {
        var prefix = preset.contains(":") ? "" : "bettercombat:";
        return new WeaponAttributesFile(prefix + preset);
    }

    @Override
    public CompletableFuture<?> run(DataWriter writer) {
        var builder = new Builder();
        generateWeaponAttributes(builder);
        var entries = builder.entries;

        List<CompletableFuture> writes = new ArrayList<>();
        for (var entry: entries) {
            var content = createFileContent(entry.preset);
            var json = gson.toJsonTree(content);
            var path = getFilePath(entry.id());
            writes.add(DataProvider.writeToPath(writer, json, path));
        }

        return CompletableFuture.allOf(writes.toArray(new CompletableFuture[0]));
    }

    @Override
    public String getName() {
        return "Weapon Attributes File Generator";
    }

    private Path getFilePath(Identifier id) {
        return this.dataOutput.getResolver(DataOutput.OutputType.DATA_PACK, "weapon_attributes").resolveJson(id);
    }
}
