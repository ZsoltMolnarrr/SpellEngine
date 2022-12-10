package net.spell_engine.attribute_assigner;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class AttributeAssigner {

    public static Map<Identifier, Assignment> assignments = new HashMap();

    public static void initialize() {
        // This might need to be replaced to something that is called earlier if unstable
        // Probably: MinecraftServer <init> TAIL
        ServerLifecycleEvents.SERVER_STARTED.register((minecraftServer) -> {
            load(minecraftServer.getResourceManager());
        });
    }

    public static class Assignment {
        public EquipmentSlot[] slots;
        public ItemAttributeModifier[] attributes;
    }

    private static void load(ResourceManager resourceManager) {
        Map<Identifier, Assignment> containers = new HashMap();
        Type jsonTypeToken = new TypeToken<Assignment>() {}.getType();
        var directory = "item_attribute_assignment";
        // Reading all attribute files
        for (var entry : resourceManager.findResources(directory, fileName -> fileName.getPath().endsWith(".json")).entrySet()) {
            var identifier = entry.getKey();
            var resource = entry.getValue();
            try {
                // System.out.println("Checking resource: " + identifier);
                JsonReader reader = new JsonReader(new InputStreamReader(resource.getInputStream()));
                var gson = new Gson();
                Assignment container = gson.fromJson(reader, jsonTypeToken);

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

    private static void process(Map<Identifier, Assignment> containers) {
        for(var entry: containers.entrySet()) {
            var array = entry.getValue().attributes;
            if (array != null && array.length > 0) {
                assignments.put(entry.getKey(), entry.getValue());
            }
        }
    }
}
