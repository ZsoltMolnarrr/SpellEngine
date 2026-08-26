package net.spell_engine.fabric.client;

import net.fabricmc.fabric.api.client.model.loading.v1.ExtraModelKey;
import net.fabricmc.fabric.api.client.model.loading.v1.FabricBakedModelManager;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.SimpleUnbakedExtraModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.resources.Identifier;
import net.spell_engine.api.render.CustomModels;
import net.spell_engine.client.render.CustomModelDiscovery;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fabric implementation of Spell Engine's raw model loading (projectiles, effects).
 * Since 1.21.4 extra models are keyed: every id gets an {@link ExtraModelKey} baked as a {@link BlockStateModel},
 * and {@link CustomModels#provider} resolves ids through those keys.
 */
public class FabricModelDiscovery implements ModelLoadingPlugin {
    private static final Map<Identifier, ExtraModelKey<BlockStateModel>> keys = new ConcurrentHashMap<>();

    public static void install() {
        ModelLoadingPlugin.register(new FabricModelDiscovery());
        CustomModels.provider = modelId -> {
            var key = keys.get(modelId);
            if (key == null) {
                return null;
            }
            return ((FabricBakedModelManager) Minecraft.getInstance().getModelManager()).getModel(key);
        };
    }

    @Override
    public void initialize(Context pluginContext) {
        var resourceManager = Minecraft.getInstance().getResourceManager();
        var discovered = CustomModelDiscovery.discoverScrollModels(resourceManager);
        for (var modelId : discovered) {
            var key = keys.computeIfAbsent(modelId, id -> ExtraModelKey.create(id::toString));
            pluginContext.addModel(key, SimpleUnbakedExtraModel.blockStateModel(modelId));
        }
    }
}
