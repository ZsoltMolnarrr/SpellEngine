package net.spell_engine.api.render;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Set;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

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
    public static void render(RenderType renderLayer, Identifier modelId,
                              PoseStack matrices, SubmitNodeCollector queue, int light, int seed) {
        var model = get(modelId);
        if (model == null) {
            if (warned.add(modelId)) {
                LOGGER.warn("Custom model not loaded: {} (is it under a discovered folder: models/spell_projectile or models/spell_effect?)", modelId);
            }
            return;
        }
        render(renderLayer, model, matrices, queue, light, seed);
    }

    public static void render(RenderType renderLayer, BlockStateModel model,
                              PoseStack matrices, SubmitNodeCollector queue, int light, int seed) {
        matrices.pushPose();
        matrices.translate(-0.5, -0.5, -0.5);
        queue.submitCustomGeometry(matrices, renderLayer, (entry, vertexConsumer) -> renderQuads(entry, vertexConsumer, model, light, seed));
        matrices.popPose();
    }

    private static final Direction[] SIDES = { null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST };

    public static void renderQuads(PoseStack.Pose entry, VertexConsumer vertexConsumer, BlockStateModel model, int light, long seed) {
        var random = RandomSource.create(seed);
        for (var part : model.collectParts(random)) {
            for (var side : SIDES) {
                for (var quad : part.getQuads(side)) {
                    vertexConsumer.putBulkData(entry, quad, 1F, 1F, 1F, 1F, light, OverlayTexture.NO_OVERLAY);
                }
            }
        }
    }
}
