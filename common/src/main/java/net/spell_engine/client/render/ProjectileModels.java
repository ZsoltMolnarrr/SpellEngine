package net.spell_engine.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.util.Identifier;
import net.spell_engine.internals.SpellRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ProjectileModels {
    public static final ArrayList<Identifier> modelIds = new ArrayList<>();
    public static final Map<Identifier, BakedModel> models = new HashMap<>();

    public static void loadIds() {
        modelIds.clear();
        for (var entry : SpellRegistry.all().entrySet()) {
            try {
                var spell = entry.getValue();
                var idString = spell.on_release.target.projectile.client_data.item_id;
                if (idString.isEmpty()) {
                    continue;
                }
                var id = new Identifier(idString);
                modelIds.add(id);
            } catch (Exception ignored) { }
        }
    }

    public static void load() {
        models.clear();
        var modelManager = MinecraftClient.getInstance().getBakedModelManager();
        System.out.println("ProjectileModels load");
        for (var id: modelIds) {
            var modelId = new ModelIdentifier(id, "inventory");
            models.put(id, modelManager.getModel(modelId));
        }
    }

    public static BakedModel getModel(Identifier id) {
//        var modelManager = MinecraftClient.getInstance().getBakedModelManager();
//        var bakedModel = models.get(id);
//        if (bakedModel == null) {
//            bakedModel = ItemRenderer.get
//        }
//        return (bakedModel == null) ? modelManager.getMissingModel() : bakedModel;
        return models.get(id);
    }
}