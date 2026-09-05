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

/// A `FabricLanguageProvider` that writes its `en_us.json` under an explicit namespace
/// instead of the data generator's mod id (e.g. `rpg_series` lang entries emitted by SpellEngine).
///
/// 1.20.1 / Fabric API 0.92: `FabricLanguageProvider` is registry-independent
/// (`generateTranslations(TranslationBuilder)`), so subclasses implement that 1-arg method.
/// The 2-arg constructor is kept so `pack.addProvider(MyLangGen::new)` keeps working with the
/// `RegistryDependentFactory` shape content mods already use.
public abstract class NamespacedLangGenerator extends FabricLanguageProvider {
    private final String languageCode;
    private final String namespace;

    protected NamespacedLangGenerator(FabricDataOutput dataOutput, String namespace) {
        super(dataOutput, "en_us");
        this.languageCode = "en_us";
        this.namespace = namespace;
    }

    protected NamespacedLangGenerator(FabricDataOutput dataOutput, CompletableFuture<RegistryWrapper.WrapperLookup> registryLookup, String namespace) {
        this(dataOutput, namespace);
    }

    // Copied from FabricLanguageProvider, only the output path differs (namespace instead of mod id)

    @Override
    public CompletableFuture<?> run(DataWriter writer) {
        TreeMap<String, String> translationEntries = new TreeMap<>();

        generateTranslations((String key, String value) -> {
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
    }

    private Path getLangFilePath(String code) {
        return dataOutput
                .getResolver(DataOutput.OutputType.RESOURCE_PACK, "lang")
                .resolveJson(new Identifier(namespace, code));
    }
}
