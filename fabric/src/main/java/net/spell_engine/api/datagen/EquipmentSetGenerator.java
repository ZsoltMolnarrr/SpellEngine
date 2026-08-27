package net.spell_engine.api.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.spell_engine.api.item.set.EquipmentSet;
import net.spell_engine.api.item.set.EquipmentSetRegistry;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class EquipmentSetGenerator implements DataProvider {
    private final CompletableFuture<HolderLookup.Provider> registryLookup;
    protected final FabricPackOutput dataOutput;

    public EquipmentSetGenerator(CompletableFuture<HolderLookup.Provider> registryLookup, FabricPackOutput dataOutput) {
        this.registryLookup = registryLookup;
        this.dataOutput = dataOutput;
    }

    public record Entry(Identifier id, EquipmentSet.Definition equipmentSet) { }

    @Override
    public CompletableFuture<?> run(CachedOutput writer) {
        return null;
    }

    @Override
    public String getName() {
        return "Equipment Set Generator";
    }

    private Path getFilePath(Identifier id) {
        return this.dataOutput.createPathProvider(PackOutput.Target.DATA_PACK, EquipmentSetRegistry.ID.getPath()).json(id);
    }
}
