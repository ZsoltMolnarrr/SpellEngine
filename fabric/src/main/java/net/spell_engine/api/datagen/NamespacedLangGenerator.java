package net.spell_engine.api.datagen;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

public abstract class NamespacedLangGenerator extends FabricLanguageProvider {
    private final CompletableFuture<HolderLookup.Provider> registryLookup;
    private final String languageCode;
    private final String namespace;
    protected NamespacedLangGenerator(FabricPackOutput dataOutput, CompletableFuture<HolderLookup.Provider> registryLookup, String namespace) {
        super(dataOutput, "en_us", registryLookup);
        this.languageCode = "en_us";
        this.registryLookup = registryLookup;
        this.namespace = namespace;
    }

    // Copied from FabricLanguageProvider

    @Override
    public CompletableFuture<?> run(CachedOutput writer) {
        TreeMap<String, String> translationEntries = new TreeMap<>();

        return this.registryLookup.thenCompose(lookup -> {
            // Fabric API 0.160 (26.3): `TranslationBuilder` is `has` + `overwrite`; the default `add` does the duplicate check
            generateTranslations(lookup, new TranslationBuilder() {
                @Override
                public boolean has(String translationKey) {
                    Objects.requireNonNull(translationKey, "translationKey");
                    return translationEntries.containsKey(translationKey);
                }

                @Override
                public String overwrite(String translationKey, String value) {
                    Objects.requireNonNull(translationKey, "translationKey");
                    Objects.requireNonNull(value, "value");
                    return translationEntries.put(translationKey, value);
                }
            });

            JsonObject langEntryJson = new JsonObject();

            for (Map.Entry<String, String> entry : translationEntries.entrySet()) {
                langEntryJson.addProperty(entry.getKey(), entry.getValue());
            }

            return DataProvider.saveStable(writer, langEntryJson, getLangFilePath(this.languageCode));
        });
    }

    @Override
    protected Path getLangFilePath(String code) {
        return packOutput
                .createPathProvider(PackOutput.Target.RESOURCE_PACK, "lang")
                .json(Identifier.fromNamespaceAndPath(namespace, code));
    }
}