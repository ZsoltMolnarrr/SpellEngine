package net.spell_engine.api.datagen;

import com.google.common.hash.Hashing;
import com.google.common.hash.HashingOutputStream;
import com.google.gson.*;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonWriter;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.minecraft.data.DataOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.DataWriter;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.fx.ParticleBatch;
import net.spell_engine.api.spell.fx.Sound;
import net.spell_engine.api.util.AlwaysGenerate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public abstract class SpellGenerator implements DataProvider {
    private final CompletableFuture<RegistryWrapper.WrapperLookup> registryLookup;
    protected final FabricDataOutput dataOutput;

    public enum OutputFormat { COMPACT, VERBOSE }
    public OutputFormat outputFormat = OutputFormat.COMPACT;

    public SpellGenerator(FabricDataOutput dataOutput, CompletableFuture<RegistryWrapper.WrapperLookup> registryLookup) {
        this.dataOutput = dataOutput;
        this.registryLookup = registryLookup;
    }

    public record Entry(Identifier id, Spell spell) { }
    public static class Builder {
        private final List<Entry> entries = new ArrayList<>();
        public void add(Identifier id, Spell spell) {
            entries.add(new Entry(id, spell));
        }
    }

    public abstract void generateSpells(Builder builder);

    private static boolean hasJsonAdapter(Class<?> clazz) {
        return clazz.isAnnotationPresent(JsonAdapter.class);
    }

    private static List<Class<?>> getAllNestedClasses(Class<?> clazz) {
        List<Class<?>> nestedClasses = new ArrayList<>();
        collectNestedClasses(clazz, nestedClasses);
        return nestedClasses;
    }

    private static void collectNestedClasses(Class<?> clazz, List<Class<?>> nestedClasses) {
        for (Class<?> nested : clazz.getDeclaredClasses()) {
            if (nested.isEnum()) {
                continue; // Skip enums
            }
            if (hasJsonAdapter(nested)) {
                continue; // Skip classes with JsonAdapter
            }
            if (!nested.getPackageName().contains("spell_engine")) {
                continue; // Skip classes outside of spell engine
            }
            nestedClasses.add(nested);
            collectNestedClasses(nested, nestedClasses); // Recursively collect deeper nested classes
        }
    }

    private static Gson compactGSON() {
        var gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(Spell.class, new DefaultValueSkippingSerializer<>(Spell.class))
                .registerTypeAdapter(ParticleBatch.class, new DefaultValueSkippingSerializer<>(ParticleBatch.class))
                .registerTypeAdapter(Sound.class, new DefaultValueSkippingSerializer<>(Sound.class));
        for (var nestedClass : getAllNestedClasses(Spell.class)) {
            gson = gson.registerTypeAdapter(nestedClass, new DefaultValueSkippingSerializer<>(nestedClass));
        }
        return gson.create();
    }

    private static final Gson verboseGSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(Spell.class, new DefaultValueSkippingSerializer<>(Spell.class))
            .create();

    @Override
    public CompletableFuture<?> run(DataWriter writer) {
        var builder = new Builder();
        generateSpells(builder);
        var entries = builder.entries;

        var gson = outputFormat == OutputFormat.COMPACT ? compactGSON() : verboseGSON;

        List<CompletableFuture> writes = new ArrayList<>();
        for (var entry: entries) {
            var spell = entry.spell;
            var spellId = entry.id;
            var json = gson.toJsonTree(spell);
            writes.add(writeOriginalFormat(writer, json, getFilePath(spellId)));
        }

        return CompletableFuture.allOf(writes.toArray(new CompletableFuture[0]));
    }

    private static CompletableFuture<?> writeOriginalFormat(DataWriter writer, JsonElement json, Path path) {
        return CompletableFuture.runAsync(() -> {
            try {
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                HashingOutputStream hashingOutputStream = new HashingOutputStream(Hashing.sha1(), byteArrayOutputStream);
                JsonWriter jsonWriter = new JsonWriter(new OutputStreamWriter(hashingOutputStream, StandardCharsets.UTF_8));

                try {
                    // Write the given json element to the json writer
                    jsonWriter.setSerializeNulls(false);
                    jsonWriter.setIndent("  ");
                    Streams.write(json, jsonWriter);
                } catch (Throwable var9) {
                    try {
                        jsonWriter.close();
                    } catch (Throwable var8) {
                        var9.addSuppressed(var8);
                    }

                    throw var9;
                }

                jsonWriter.close();
                writer.write(path, byteArrayOutputStream.toByteArray(), hashingOutputStream.hash());
            } catch (IOException var10) {
                IOException iOException = var10;
                LOGGER.error("Failed to save file to {}", path, iOException);
            }

        }, Util.getMainWorkerExecutor());
    }

    @Override
    public String getName() {
        return "Spell Generator";
    }

    private Path getFilePath(Identifier spellId) {
        return this.dataOutput.getResolver(DataOutput.OutputType.DATA_PACK, "spell").resolveJson(spellId);
    }



    public static class DefaultValueSkippingSerializer<T> implements JsonSerializer<T> {
        private final T defaultInstance;

        public DefaultValueSkippingSerializer(Class<T> clazz) {
            try {
                this.defaultInstance = clazz.getDeclaredConstructor().newInstance(); // Create a default instance
            } catch (Exception e) {
                throw new RuntimeException("Failed to create default instance for class: " + clazz.getName(), e);
            }
        }

        @Override
        public JsonElement serialize(T src, java.lang.reflect.Type typeOfSrc, JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();

            try {
                for (Field field : src.getClass().getDeclaredFields()) {
                    field.setAccessible(true); // Allow access to private fields

                    Object value = field.get(src);
                    Object defaultValue = field.get(defaultInstance);

                    // Skip null values
                    if (value == null) continue;

//                    if (!value.equals(defaultValue)) {
//                        jsonObject.add(field.getName(), context.serialize(value));
//                    }
                    if (field.isAnnotationPresent(AlwaysGenerate.class) || !objectsJSONEqual(value, defaultValue)) {
                        jsonObject.add(field.getName(), context.serialize(value));
                    }
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Error accessing fields", e);
            }

            return jsonObject;
        }

        // Check if the field is a custom object (not primitive, String, or wrapper)
        private boolean isCustomObject(Class<?> clazz) {
            return !clazz.isPrimitive() &&
                    !Number.class.isAssignableFrom(clazz) &&
                    !Boolean.class.isAssignableFrom(clazz) &&
                    !Character.class.isAssignableFrom(clazz) &&
                    !String.class.isAssignableFrom(clazz);
        }

        private static final Gson checkerGson = new GsonBuilder().create();
        private static boolean objectsJSONEqual(Object a, Object b) {
            var jsonA = checkerGson.toJson(a);
            var jsonB = checkerGson.toJson(b);
            return jsonA.equals(jsonB);
        }
    }
}
