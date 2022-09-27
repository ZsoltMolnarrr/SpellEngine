package net.combatspells.attribute_assigner;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AttributeAssigner {

    public static Map<Identifier, List<ItemAttributeModifier>> assignemnts = new HashMap();

    public static void initialize() {
        // This might need to be replaced to something that is called earlier if unstable
        // Probably: MinecraftServer <init> TAIL
        ServerLifecycleEvents.SERVER_STARTED.register((minecraftServer) -> {
            load(minecraftServer.getResourceManager());
        });
    }

    public static class FileFormat {
        ItemAttributeModifier[] attributes;
    }

    private static void load(ResourceManager resourceManager) {
        Map<Identifier, FileFormat> containers = new HashMap();
        Type jsonTypeToken = new TypeToken<FileFormat>() {}.getType();
        var directory = "item_attribute_assignment";
        // Reading all attribute files
        for (var entry : resourceManager.findResources(directory, fileName -> fileName.getPath().endsWith(".json")).entrySet()) {
            var identifier = entry.getKey();
            var resource = entry.getValue();
            try {
                // System.out.println("Checking resource: " + identifier);
                JsonReader reader = new JsonReader(new InputStreamReader(resource.getInputStream()));
                var gson = new Gson();
                FileFormat container = gson.fromJson(reader, jsonTypeToken);

                var id = identifier
                        .toString().replace(directory + "/", "");
                id = id.substring(0, id.lastIndexOf('.'));
                containers.put(new Identifier(id), container);
            } catch (Exception e) {
                System.err.println("Failed to parse: " + directory + "/" + identifier);
                e.printStackTrace();
            }
        }

        process(containers);
    }

    private static void process(Map<Identifier, FileFormat> containers) {
        for(var entry: containers.entrySet()) {
            var array = entry.getValue().attributes;
            if (array != null && array.length > 0) {
                var list = List.of(array);
                assignemnts.put(entry.getKey(), list);
            }
        }
    }
}
