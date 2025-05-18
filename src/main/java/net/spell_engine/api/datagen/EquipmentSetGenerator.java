package net.spell_engine.api.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.minecraft.data.DataOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.DataWriter;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import net.spell_engine.api.item.set.EquipmentSet;
import net.spell_engine.api.item.set.EquipmentSetRegistry;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class EquipmentSetGenerator implements DataProvider {
    private final CompletableFuture<RegistryWrapper.WrapperLookup> registryLookup;
    protected final FabricDataOutput dataOutput;

    public EquipmentSetGenerator(CompletableFuture<RegistryWrapper.WrapperLookup> registryLookup, FabricDataOutput dataOutput) {
        this.registryLookup = registryLookup;
        this.dataOutput = dataOutput;
    }

    public record Entry(Identifier id, EquipmentSet.Definition equipmentSet) { }

    @Override
    public CompletableFuture<?> run(DataWriter writer) {
        return null;
    }

    @Override
    public String getName() {
        return "Equipment Set Generator";
    }

    private Path getFilePath(Identifier id) {
        return this.dataOutput.getResolver(DataOutput.OutputType.DATA_PACK, EquipmentSetRegistry.ID.getPath()).resolveJson(id);
    }
}
