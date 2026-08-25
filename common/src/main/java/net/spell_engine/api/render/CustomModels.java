package net.spell_engine.api.render;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/// Raw JSON models (`models/spell_projectile/*`, `models/spell_effect/*`, …) rendered by Spell Engine.
///
/// Since 1.21.4 baked models are no longer addressable by identifier: they are loaded as "extra"
/// (Fabric: `ExtraModelKey`) / "standalone" (NeoForge) models keyed at registration. The platform module
/// registers every id discovered by `CustomModelDiscovery` and installs a [ModelProvider] that resolves them.
/// Rendering goes through the render command queue (1.21.9+), as a custom command drawing the model's quads.
public class CustomModels {
    private static final Logger LOGGER = LoggerFactory.getLogger("SpellEngine/CustomModels");

    public interface ModelProvider {
        @Nullable BlockStateModel get(Identifier modelId);
    }

    /// Installed by the platform client initializer
    public static ModelProvider provider = modelId -> null;

    @Nullable
    public static BlockStateModel get(Identifier modelId) {
        return provider.get(modelId);
    }

    private static final Set<Identifier> warned = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /// Submits the model to the queue on the given layer; a no-op (logged once) when the model is unknown.
    public static void render(RenderLayer renderLayer, Identifier modelId,
                              MatrixStack matrices, OrderedRenderCommandQueue queue, int light, int seed) {
        var model = get(modelId);
        if (model == null) {
            if (warned.add(modelId)) {
                LOGGER.warn("Custom model not loaded: {} (is it under a discovered folder: models/spell_projectile or models/spell_effect?)", modelId);
            }
            return;
        }
        render(renderLayer, model, matrices, queue, light, seed);
    }

    public static void render(RenderLayer renderLayer, BlockStateModel model,
                              MatrixStack matrices, OrderedRenderCommandQueue queue, int light, int seed) {
        matrices.push();
        matrices.translate(-0.5, -0.5, -0.5);
        queue.submitCustom(matrices, renderLayer, (entry, vertexConsumer) -> renderQuads(entry, vertexConsumer, model, light, seed));
        matrices.pop();
    }

    private static final Direction[] SIDES = { null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST };

    public static void renderQuads(MatrixStack.Entry entry, VertexConsumer vertexConsumer, BlockStateModel model, int light, long seed) {
        var random = Random.create(seed);
        for (var part : model.getParts(random)) {
            for (var side : SIDES) {
                for (var quad : part.getQuads(side)) {
                    vertexConsumer.quad(entry, quad, 1F, 1F, 1F, 1F, light, OverlayTexture.DEFAULT_UV);
                }
            }
        }
    }
}
