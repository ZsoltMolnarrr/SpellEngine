package net.spell_engine.api.datagen;

import com.google.common.hash.Hashing;
import com.google.common.hash.HashingOutputStream;
import com.google.gson.*;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonWriter;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.fx.Easing;
import net.spell_engine.api.spell.fx.Fx;
import net.spell_engine.api.spell.fx.ModelEffect;
import net.spell_engine.api.spell.fx.PlayerAnimation;
import net.spell_engine.api.spell.fx.ParticleGroup;
import net.spell_engine.api.spell.fx.Sound;
import net.spell_engine.api.spell.summon.SummonBehaviour;
import net.spell_engine.api.util.AlwaysGenerate;
import net.spell_engine.api.util.NeverGenerate;

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
    private final CompletableFuture<HolderLookup.Provider> registryLookup;
    protected final FabricDataOutput dataOutput;

    public enum OutputFormat { COMPACT, VERBOSE }
    public OutputFormat outputFormat = OutputFormat.COMPACT;

    public SpellGenerator(FabricDataOutput dataOutput, CompletableFuture<HolderLookup.Provider> registryLookup) {
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
                .registerTypeAdapter(Sound.class, new DefaultValueSkippingSerializer<>(Sound.class))
                .registerTypeAdapter(PlayerAnimation.class, new DefaultValueSkippingSerializer<>(PlayerAnimation.class))
                .registerTypeAdapter(ModelEffect.class, new DefaultValueSkippingSerializer<>(ModelEffect.class))
                // SummonBehaviour (referenced by the SUMMON impact) is a separate top-level type, so
                // its tree isn't covered by getAllNestedClasses(Spell.class) — register it explicitly.
                .registerTypeAdapter(SummonBehaviour.class, new DefaultValueSkippingSerializer<>(SummonBehaviour.class))
                // Same for ParticleGroup and its `particle`/`batch` blocks: both are top-level
                // types under api.spell.fx. Without these, every effect serialized all ~30 fields of
                // Particle and Batch, defaults included.
                .registerTypeAdapter(ParticleGroup.class, new DefaultValueSkippingSerializer<>(ParticleGroup.class))
                // Same again for the FX bundle every one-shot site now carries: it lives under
                // api.spell.fx rather than inside Spell, so without this an untouched `visuals`
                // would write out `{"particles": [], "models": []}` at every site that has one.
                .registerTypeAdapter(Fx.Visuals.class, new DefaultValueSkippingSerializer<>(Fx.Visuals.class))
                .registerTypeAdapter(Easing.Curve.class, new DefaultValueSkippingSerializer<>(Easing.Curve.class));
        for (var nestedClass : getAllNestedClasses(Spell.class)) {
            gson = gson.registerTypeAdapter(nestedClass, new DefaultValueSkippingSerializer<>(nestedClass));
        }
        for (var nestedClass : getAllNestedClasses(SummonBehaviour.class)) {
            gson = gson.registerTypeAdapter(nestedClass, new DefaultValueSkippingSerializer<>(nestedClass));
        }
        for (var nestedClass : getAllNestedClasses(ParticleGroup.class)) {
            gson = gson.registerTypeAdapter(nestedClass, new DefaultValueSkippingSerializer<>(nestedClass));
        }
        return gson.create();
    }

    private static final Gson verboseGSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(Spell.class, new DefaultValueSkippingSerializer<>(Spell.class))
            .create();

    @Override
    public CompletableFuture<?> run(CachedOutput writer) {
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

    private static CompletableFuture<?> writeOriginalFormat(CachedOutput writer, JsonElement json, Path path) {
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
                writer.writeIfNeeded(path, byteArrayOutputStream.toByteArray(), hashingOutputStream.hash());
            } catch (IOException var10) {
                IOException iOException = var10;
                LOGGER.error("Failed to save file to {}", path, iOException);
            }

        }, Util.backgroundExecutor());
    }

    @Override
    public String getName() {
        return "Spell Generator";
    }

    private Path getFilePath(Identifier spellId) {
        return this.dataOutput.createPathProvider(PackOutput.Target.DATA_PACK, "spell").json(spellId);
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
                    // Mirror Gson's default Excluder: never serialize static or transient fields.
                    // E.g. SummonBehaviour's `static final Gson GSON` (a Gson holds a ThreadLocal,
                    // which can't be made accessible under JDK 17+) and its `transient Supplier` caches.
                    int modifiers = field.getModifiers();
                    if (java.lang.reflect.Modifier.isStatic(modifiers)
                            || java.lang.reflect.Modifier.isTransient(modifiers)) {
                        continue;
                    }
                    field.setAccessible(true); // Allow access to private fields

                    Object value = field.get(src);
                    Object defaultValue = field.get(defaultInstance);

                    // Skip null values
                    if (value == null) continue;

//                    if (!value.equals(defaultValue)) {
//                        jsonObject.add(field.getName(), context.serialize(value));
//                    }
                    if (field.isAnnotationPresent(NeverGenerate.class)) {
                        continue; // Skip this field entirely
                    }
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
