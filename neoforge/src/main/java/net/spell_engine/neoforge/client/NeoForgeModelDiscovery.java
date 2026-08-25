package net.spell_engine.neoforge.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.util.Identifier;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;
import net.spell_engine.api.render.CustomModels;
import net.spell_engine.client.render.CustomModelDiscovery;
import net.spell_engine.client.render.CustomModelRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NeoForge implementation of Spell Engine's raw model loading (projectiles, effects, explicitly registered ids).
 * Since 1.21.4 extra models are keyed: every id gets a {@link StandaloneModelKey} baked as a {@link BlockStateModel},
 * and {@link CustomModels#provider} resolves ids through those keys.
 */
public class NeoForgeModelDiscovery {
    private static final Logger LOGGER = LoggerFactory.getLogger("SpellEngine/NeoForgeModelDiscovery");
    private static final Map<Identifier, StandaloneModelKey<BlockStateModel>> keys = new ConcurrentHashMap<>();

    public static void install() {
        CustomModels.provider = modelId -> {
            var key = keys.get(modelId);
            if (key == null) {
                return null;
            }
            return MinecraftClient.getInstance().getBakedModelManager().getStandaloneModel(key);
        };
    }

    /// Called from the {@link ModelEvent.RegisterStandalone} mod bus event.
    public static void register(ModelEvent.RegisterStandalone event) {
        try {
            var resourceManager = MinecraftClient.getInstance().getResourceManager();
            var discovered = CustomModelDiscovery.discoverScrollModels(resourceManager);
            var all = CustomModelRegistry.allModelIds(discovered);
            for (var modelId : all) {
                var key = keys.computeIfAbsent(modelId, id -> new StandaloneModelKey<>(id::toString));
                event.register(key, SimpleUnbakedStandaloneModel.blockStateModel(modelId));
            }
            LOGGER.info("Registered {} standalone spell models", all.size());
        } catch (Exception e) {
            LOGGER.error("Error registering spell models for NeoForge", e);
        }
    }
}
