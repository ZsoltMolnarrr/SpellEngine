package net.spell_engine.api.client;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.spell_engine.client.render.CustomModelRegistry;
import net.spell_engine.mixin.client.render.ItemRendererAccessor;

import java.util.List;

public class CustomModels {
    public static void registerModelIds(List<Identifier> ids) {
        CustomModelRegistry.modelIds.addAll(ids);
    }

    public static void render(RenderLayer renderLayer, ItemRenderer itemRenderer, Identifier modelId,
                              MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int seed) {
        var model = CustomModelRegistry.getModel(modelId);
        if (model == null) {
            var stack = Registry.ITEM.get(modelId).getDefaultStack();
            if (!stack.isEmpty()) {
                model = itemRenderer.getModel(stack, null, null, seed);
            }
        }
        var buffer = vertexConsumers.getBuffer(renderLayer);
        matrices.translate(-0.5, -0.5, -0.5);
        ((ItemRendererAccessor)itemRenderer).renderBakedItemModel(model, ItemStack.EMPTY, light, OverlayTexture.DEFAULT_UV, matrices, buffer);
    }
}
