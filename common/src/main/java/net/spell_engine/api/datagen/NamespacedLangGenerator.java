package net.spell_engine.api.datagen;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider;
import net.minecraft.data.DataOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.DataWriter;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

public abstract class NamespacedLangGenerator extends FabricLanguageProvider {
    private final CompletableFuture<RegistryWrapper.WrapperLookup> registryLookup;
    private final String languageCode;
    private final String namespace;
    protected NamespacedLangGenerator(FabricDataOutput dataOutput, CompletableFuture<RegistryWrapper.WrapperLookup> registryLookup, String namespace) {
        super(dataOutput, "en_us", registryLookup);
        this.languageCode = "en_us";
        this.registryLookup = registryLookup;
        this.namespace = namespace;
    }

    // Copied from FabricLanguageProvider

    @Override
    public CompletableFuture<?> run(DataWriter writer) {
        TreeMap<String, String> translationEntries = new TreeMap<>();

        return this.registryLookup.thenCompose(lookup -> {
            generateTranslations(lookup, (String key, String value) -> {
                Objects.requireNonNull(key);
                Objects.requireNonNull(value);

                if (translationEntries.containsKey(key)) {
                    throw new RuntimeException("Existing translation key found - " + key + " - Duplicate will be ignored.");
                }

                translationEntries.put(key, value);
            });

            JsonObject langEntryJson = new JsonObject();

            for (Map.Entry<String, String> entry : translationEntries.entrySet()) {
                langEntryJson.addProperty(entry.getKey(), entry.getValue());
            }

            return DataProvider.writeToPath(writer, langEntryJson, getLangFilePath(this.languageCode));
        });
    }

    private Path getLangFilePath(String code) {
        return dataOutput
                .getResolver(DataOutput.OutputType.RESOURCE_PACK, "lang")
                .resolveJson(Identifier.of(namespace, code));
    }
}